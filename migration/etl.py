#!/usr/bin/env python3
"""Repeatable legacy-MySQL to qyd-platform ETL. It never runs without explicit DB URLs."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable, Mapping
from urllib.parse import unquote, urlparse

import mysql.connector


@dataclass(frozen=True)
class TableSpec:
    entity: str
    source_table: str
    target_table: str
    source_pk: str
    target_columns: tuple[str, ...]


class Mapper:
    spec: TableSpec
    def map(self, row: Mapping[str, Any]) -> dict[str, Any]:
        raise NotImplementedError

    @staticmethod
    def stable_id(entity: str, legacy_id: Any) -> str:
        digest=hashlib.sha256(f"{entity}:{legacy_id}".encode()).hexdigest()
        return f"{digest[:8]}-{digest[8:12]}-4{digest[13:16]}-a{digest[17:20]}-{digest[20:32]}"


class UserMapper(Mapper):
    spec=TableSpec("user","ayduser","qyd_user","id",
                   ("id","username","password_hash","role","enabled","created_at","updated_at","version"))
    valid_roles={"CUSTOMER","PLATFORM_ADMIN","OPERATOR","FINANCE","VENUE_ADMIN","VENUE_STAFF"}
    def __init__(self,role_map:Mapping[int,str]|None=None):
        # Legacy code proves userType=1 means ordinary customer. Other values must be
        # explicitly reconciled with the legacy usertype table before migration.
        self.role_map=dict(role_map or {1:"CUSTOMER"})
        if not self.role_map or set(self.role_map.values())-self.valid_roles:
            raise ValueError("legacy userType role map contains unsupported roles")
    def map(self,row: Mapping[str,Any])->dict[str,Any]:
        now=datetime.now(timezone.utc)
        legacy_id=row[self.spec.source_pk]
        user_type=int(row.get("userType") if row.get("userType") is not None else row.get("usertype",0))
        if user_type not in self.role_map:
            raise ValueError(f"unmapped legacy userType {user_type}; reconcile it before migration")
        # Never copy pwd. A deterministic, deliberately unsupported hash plus
        # enabled=false keeps reruns stable and blocks login until an audited reset.
        disabled_hash="!MIGRATION_DISABLED!"+hashlib.sha256(
            f"disabled-user:{legacy_id}".encode()
        ).hexdigest()
        return {"id":self.stable_id("user",legacy_id),
                "username":row.get("userName") or row.get("username") or f"migrated_{legacy_id}",
                "password_hash":disabled_hash,"role":self.role_map[user_type],"enabled":False,
                "created_at":row.get("createTime") or row.get("createtime") or now,
                "updated_at":row.get("lastTime") or row.get("createTime") or now,"version":0}


class VenueMapper(Mapper):
    spec=TableSpec("venue","shop","venue","id",
                   ("id","name","address","status","created_at","updated_at","version"))
    def map(self,row: Mapping[str,Any])->dict[str,Any]:
        now=datetime.now(timezone.utc)
        legacy_id=row[self.spec.source_pk]
        return {"id":self.stable_id("venue",legacy_id),
                "name":row.get("name") or f"迁移场馆-{legacy_id}",
                "address":row.get("address"),"status":"ACTIVE" if row.get("status")==1 else "INACTIVE",
                "created_at":row.get("createTime") or row.get("createtime") or now,
                "updated_at":row.get("updateTime") or row.get("updatetime") or row.get("createTime") or now,
                "version":0}


class ExtensionMapper(Mapper):
    """Safe extension point: configure columns only after legacy schema reconciliation."""
    def __init__(self,entity:str,source:str,target:str,pk:str,columns:Iterable[str]):
        self.spec=TableSpec(entity,source,target,pk,tuple(columns))
    def map(self,row:Mapping[str,Any])->dict[str,Any]:
        raise NotImplementedError(
            f"{self.spec.entity} mapping must be implemented after source-field/status reconciliation")


MAPPERS: dict[str,Mapper] = {
    "user":UserMapper(),
    "venue":VenueMapper(),
    "order":ExtensionMapper("order","orders","customer_order","id",()),
    "payment":ExtensionMapper("payment","payment","payment_order","id",()),
    "refund":ExtensionMapper("refund","refund","payment_refund","id",()),
    "settlement":ExtensionMapper("settlement","settlement","settlement_batch","id",()),
}


@dataclass
class MigrationReport:
    entity:str
    read:int=0
    written:int=0
    skipped:int=0
    failed:int=0
    target_count:int|None=None
    id_map_count:int|None=None
    first_error:str|None=None
    started_at:str=""
    finished_at:str=""


class Runner:
    def __init__(self,source:Any,target:Any,batch_size:int,dry_run:bool,state_dir:Path):
        self.source=source;self.target=target;self.batch_size=batch_size
        self.dry_run=dry_run;self.state_dir=state_dir
        state_dir.mkdir(parents=True,exist_ok=True)

    def prepare(self)->None:
        if self.dry_run:return
        cursor=self.target.cursor()
        cursor.execute("""CREATE TABLE IF NOT EXISTS migration_id_map(
          entity VARCHAR(40) NOT NULL, legacy_id VARCHAR(100) NOT NULL, target_id VARCHAR(36) NOT NULL,
          migrated_at TIMESTAMP(6) NOT NULL, PRIMARY KEY(entity,legacy_id), UNIQUE(entity,target_id))""")
        self.target.commit()

    def migrate(self,mapper:Mapper)->MigrationReport:
        spec=mapper.spec
        checkpoint=self._checkpoint(spec.entity)
        report=MigrationReport(spec.entity,started_at=datetime.now(timezone.utc).isoformat())
        while True:
            cursor=self.source.cursor(dictionary=True)
            cursor.execute(f"SELECT * FROM `{spec.source_table}` WHERE `{spec.source_pk}` > %s "
                           f"ORDER BY `{spec.source_pk}` LIMIT %s",(checkpoint,self.batch_size))
            rows=cursor.fetchall()
            if not rows:break
            for row in rows:
                report.read+=1
                legacy_id=row[spec.source_pk]
                try:
                    mapped=mapper.map(row)
                    if not self.dry_run:self._write(spec,legacy_id,mapped)
                    report.written+=1
                    checkpoint=legacy_id
                    self._save_checkpoint(spec.entity,checkpoint)
                except Exception as exc:
                    report.failed+=1
                    report.first_error=report.first_error or f"{legacy_id}: {exc}"
                    if not self.dry_run:self.target.rollback()
                    self._write_report(report)
                    raise
            if not self.dry_run:self.target.commit()
        report.finished_at=datetime.now(timezone.utc).isoformat()
        if not self.dry_run:
            cursor=self.target.cursor()
            cursor.execute(f"SELECT COUNT(*) FROM `{spec.target_table}`")
            report.target_count=int(cursor.fetchone()[0])
            cursor.execute("SELECT COUNT(*) FROM migration_id_map WHERE entity=%s",(spec.entity,))
            report.id_map_count=int(cursor.fetchone()[0])
        self._write_report(report)
        return report

    def _write(self,spec:TableSpec,legacy_id:Any,mapped:Mapping[str,Any])->None:
        columns=spec.target_columns
        if set(columns)!=set(mapped):raise ValueError(f"mapped columns differ: {set(mapped)^set(columns)}")
        cursor=self.target.cursor()
        placeholders=",".join(["%s"]*len(columns))
        updates=",".join(f"`{c}`=VALUES(`{c}`)" for c in columns if c!="id")
        cursor.execute(f"INSERT INTO `{spec.target_table}` ({','.join(f'`{c}`' for c in columns)}) "
                       f"VALUES ({placeholders}) ON DUPLICATE KEY UPDATE {updates}",tuple(mapped[c] for c in columns))
        cursor.execute("INSERT INTO migration_id_map(entity,legacy_id,target_id,migrated_at) VALUES(%s,%s,%s,%s) "
                       "ON DUPLICATE KEY UPDATE target_id=VALUES(target_id),migrated_at=VALUES(migrated_at)",
                       (spec.entity,str(legacy_id),mapped["id"],datetime.now(timezone.utc)))

    def _checkpoint(self,entity:str)->Any:
        path=self.state_dir/f"{entity}.checkpoint"
        if not path.exists():return 0
        raw=path.read_text(encoding="utf-8").strip()
        try:return int(raw)
        except ValueError:return raw

    def _save_checkpoint(self,entity:str,value:Any)->None:
        if not self.dry_run:(self.state_dir/f"{entity}.checkpoint").write_text(str(value),encoding="utf-8")

    def _write_report(self,report:MigrationReport)->None:
        (self.state_dir/f"{report.entity}.report.json").write_text(
            json.dumps(asdict(report),ensure_ascii=False,indent=2),encoding="utf-8")


def connect(url:str):
    parsed=urlparse(url)
    if parsed.scheme not in {"mysql","mysql+mysqlconnector"}:raise ValueError("only mysql:// URLs are accepted")
    return mysql.connector.connect(host=parsed.hostname,port=parsed.port or 3306,user=unquote(parsed.username or ""),
                                   password=unquote(parsed.password or ""),database=parsed.path.lstrip("/"))


def main()->int:
    parser=argparse.ArgumentParser()
    parser.add_argument("entities",nargs="+",choices=tuple(MAPPERS))
    parser.add_argument("--source-url",default=os.getenv("LEGACY_DB_URL"))
    parser.add_argument("--target-url",default=os.getenv("TARGET_DB_URL"))
    parser.add_argument("--batch-size",type=int,default=int(os.getenv("BATCH_SIZE","500")))
    parser.add_argument("--state-dir",type=Path,default=Path(os.getenv("STATE_DIR",".migration-state")))
    parser.add_argument("--legacy-usertype-role-map",default=os.getenv("LEGACY_USERTYPE_ROLE_MAP"),
                        help='audited JSON map, e.g. {"1":"CUSTOMER","2":"VENUE_ADMIN"}')
    parser.add_argument("--dry-run",action="store_true")
    args=parser.parse_args()
    if not args.source_url:parser.error("LEGACY_DB_URL or --source-url is required")
    if not args.dry_run and not args.target_url:parser.error("TARGET_DB_URL or --target-url is required unless --dry-run")
    source=connect(args.source_url);target=connect(args.target_url) if args.target_url else None
    runner=Runner(source,target,args.batch_size,args.dry_run,args.state_dir);runner.prepare()
    try:
        for entity in args.entities:
            mapper=MAPPERS[entity]
            if entity=="user" and args.legacy_usertype_role_map:
                raw=json.loads(args.legacy_usertype_role_map)
                mapper=UserMapper({int(key):str(value) for key,value in raw.items()})
            print(json.dumps(asdict(runner.migrate(mapper)),ensure_ascii=False))
    finally:
        source.close()
        if target:target.close()
    return 0


if __name__=="__main__":
    sys.exit(main())
