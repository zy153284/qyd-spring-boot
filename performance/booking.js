import http from 'k6/http'
import { check, sleep } from 'k6'
import { Counter } from 'k6/metrics'

const BASE_URL=__ENV.BASE_URL || 'http://localhost:8080/api'
const TOKEN=__ENV.ACCESS_TOKEN || ''
const SKU_ID=__ENV.SKU_ID || ''
const SLOT_ID=__ENV.SLOT_ID || ''
const idempotencyViolations=new Counter('idempotency_violations')

export const options={
  scenarios:{
    browse:{executor:'constant-vus',vus:10,duration:'30s',exec:'browse'},
    same_slot_orders:{executor:'shared-iterations',vus:20,iterations:40,maxDuration:'45s',exec:'order'},
  },
  thresholds:{
    http_req_failed:['rate<0.02'],
    http_req_duration:['p(95)<500','p(99)<1200'],
    checks:['rate>0.98'],
    idempotency_violations:['count==0'],
  },
}

const auth=()=>({headers:{Authorization:`Bearer ${TOKEN}`,'Content-Type':'application/json'}})

export function setup(){
  if(!TOKEN) throw new Error('ACCESS_TOKEN is required')
  if(!SKU_ID||!SLOT_ID) throw new Error('SKU_ID and SLOT_ID are required')
}

export function browse(){
  const venues=http.get(`${BASE_URL}/v1/venues?status=ACTIVE`,auth())
  check(venues,{'venue search 200':r=>r.status===200})
  const slots=http.get(`${BASE_URL}/v1/inventory/slots?skuId=${encodeURIComponent(SKU_ID)}`,auth())
  check(slots,{'inventory query 200':r=>r.status===200})
  sleep(0.2)
}

export function order(){
  const key=`k6-${__VU}-${__ITER}`
  const body=JSON.stringify({items:[{skuId:SKU_ID,slotId:SLOT_ID,quantity:1}]})
  const params={...auth(),headers:{...auth().headers,'Idempotency-Key':key}}
  const responses=http.batch([
    ['POST',`${BASE_URL}/v1/orders`,body,params],
    ['POST',`${BASE_URL}/v1/orders`,body,params],
  ])
  const accepted=responses.every(r=>[200,201,409].includes(r.status))
  check(responses[0],{'order contract status':()=>accepted})
  if(responses[0].status<300&&responses[1].status<300){
    try{
      if(responses[0].json('id')!==responses[1].json('id')) idempotencyViolations.add(1)
    }catch(_error){idempotencyViolations.add(1)}
  }
}
