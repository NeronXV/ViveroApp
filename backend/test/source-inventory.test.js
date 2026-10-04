import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {reconcileSourceInventory,quantityMilli} from '../scripts/source-inventory.js';
import {sourceInventoryFixture} from './fixtures/source-inventory-demo.js';
const inspect=source=>{const bytes=Buffer.from(JSON.stringify(source));return reconcileSourceInventory(bytes,createHash('sha256').update(bytes).digest('hex'));};
test('inventory reconciliation preserves exact milli quantities, transfer pairs and count links',()=>{
  const result=inspect(sourceInventoryFixture());assert.deepEqual(result.counts,{balances:2,balance_scopes:2,derived_zero_minimum_scopes:0,movements:4,counts:2,transfer_pairs:1});
  assert.equal(quantityMilli('-0.125'),-125n);assert.equal(Object.keys(result.count_movements).length,1);
});
test('inventory rejects incomplete ledgers, transfers, counts, precision and unsupported locations',()=>{
  for(const mutate of [
    t=>{t.inventory_balances[0].total_quantity=8;},
    t=>{t.inventory_movements[1].quantity=-2;},
    t=>{t.inventory_counts[0].previous_quantity=4;},
    t=>{t.inventory_counts[1].adjustment_quantity=1;},
    t=>{t.inventory_counts[1].previous_quantity=8;t.inventory_counts[1].counted_quantity=8;},
    t=>{t.inventory_movements[0].quantity=0.0001;},
    t=>{t.inventory_movements[0].location_id='missing';},
    t=>{t.inventory_movements[0].movement_type='SALE';},
  ]){const value=sourceInventoryFixture();mutate(value.tables);assert.throws(()=>inspect(value));}
});
test('global minimum creates only missing zero scopes, without inventing movements',()=>{
  const source=sourceInventoryFixture();source.tables.branches.push({id:'00000000-0000-4000-8000-000000000099',code:'THIRD',name:'Third demo',is_active:true});
  const report=inspect(source);assert.equal(report.counts.balances,2);assert.equal(report.counts.balance_scopes,3);assert.equal(report.counts.derived_zero_minimum_scopes,1);assert.equal(report.counts.movements,4);
  assert.equal(report.scopes.find(row=>row.derived_from_product_minimum).total_quantity,0);
});
