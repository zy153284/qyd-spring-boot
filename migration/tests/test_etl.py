import tempfile
import unittest
from pathlib import Path

from etl import ExtensionMapper, Runner, UserMapper, VenueMapper


class FakeCursor:
    def __init__(self,rows):self.rows=rows
    def execute(self,sql,params):self.params=params
    def fetchall(self):
        after,limit=self.params
        return [row for row in self.rows if row["id"]>after][:limit]


class FakeConnection:
    def __init__(self,rows):self.rows=rows
    def cursor(self,dictionary=False):return FakeCursor(self.rows)
    def close(self):pass


class EtlTest(unittest.TestCase):
    def test_mapper_tables_match_legacy_and_flyway_v1(self):
        self.assertEqual(("ayduser","qyd_user"),(UserMapper.spec.source_table,UserMapper.spec.target_table))
        self.assertEqual(("shop","venue"),(VenueMapper.spec.source_table,VenueMapper.spec.target_table))

    def test_user_mapping_is_stable_and_forces_password_reset(self):
        mapper=UserMapper()
        first=mapper.map({"id":7,"userName":"alice","userType":1,"pwd":"legacy-plaintext"})
        second=mapper.map({"id":7,"userName":"alice","userType":1,"pwd":"legacy-plaintext"})
        self.assertEqual(first["id"],second["id"])
        self.assertEqual("CUSTOMER",first["role"])
        self.assertFalse(first["enabled"])
        self.assertTrue(first["password_hash"].startswith("!MIGRATION_DISABLED!"))
        self.assertNotIn("legacy-plaintext",first["password_hash"])
        self.assertEqual(first["password_hash"],second["password_hash"])
        self.assertEqual(set(mapper.spec.target_columns),set(first))

    def test_usertype_mapping_is_explicit_and_rejects_unknown_values(self):
        mapper=UserMapper({1:"CUSTOMER",2:"VENUE_ADMIN",3:"VENUE_STAFF",4:"OPERATOR",
                           5:"FINANCE",6:"PLATFORM_ADMIN"})
        for legacy,expected in mapper.role_map.items():
            self.assertEqual(expected,mapper.map({"id":legacy,"userName":f"u{legacy}","userType":legacy})["role"])
        with self.assertRaisesRegex(ValueError,"unmapped legacy userType"):
            UserMapper().map({"id":8,"userName":"unknown","userType":99})

    def test_venue_columns_match_v1_and_status_is_fail_closed(self):
        mapped=VenueMapper().map({"id":9,"name":"旧场馆","address":"旧地址","status":1})
        self.assertEqual("ACTIVE",mapped["status"])
        self.assertNotIn("merchant_id",mapped)
        self.assertEqual(set(VenueMapper.spec.target_columns),set(mapped))

    def test_dry_run_batches_without_target_writes_or_checkpoint(self):
        rows=[{"id":1,"userName":"a","userType":1},{"id":2,"userName":"b","userType":1},
              {"id":3,"userName":"c","userType":1}]
        with tempfile.TemporaryDirectory() as directory:
            runner=Runner(FakeConnection(rows),None,2,True,Path(directory))
            report=runner.migrate(UserMapper())
            self.assertEqual((3,3,0),(report.read,report.written,report.failed))
            self.assertFalse((Path(directory)/"user.checkpoint").exists())
            self.assertTrue((Path(directory)/"user.report.json").exists())

    def test_dry_run_does_not_modify_existing_checkpoint(self):
        with tempfile.TemporaryDirectory() as directory:
            checkpoint=Path(directory)/"user.checkpoint"
            checkpoint.write_text("1",encoding="utf-8")
            runner=Runner(FakeConnection([{"id":2,"userName":"b","userType":1}]),None,10,True,Path(directory))
            self.assertEqual(1,runner.migrate(UserMapper()).written)
            self.assertEqual("1",checkpoint.read_text(encoding="utf-8"))

    def test_unreconciled_financial_mapper_fails_closed(self):
        mapper=ExtensionMapper("payment","payment","payment_order","id",())
        with self.assertRaisesRegex(NotImplementedError,"must be implemented"):
            mapper.map({"id":1})


if __name__=="__main__":
    unittest.main()
