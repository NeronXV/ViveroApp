import { SOURCE_TABLES } from '../../scripts/source-export-tables.js';
export function sourceHistoryFixture() {
  const id = n => `00000000-0000-4000-8000-${String(n).padStart(12,'0')}`;
  const date = '2026-10-01T12:00:00.123456Z';
  const tables = Object.fromEntries(SOURCE_TABLES.map(name => [name,[]]));
  tables.branches = [{ id:id(1),code:'MATRIZ',name:'Demo Matriz',is_active:true },{ id:id(2),code:'CENTRO',name:'Demo Centro',is_active:true }];
  tables.auth_users = [{id:id(3),email:'owner@example.invalid'}];
  tables.profiles = [{id:id(3),full_name:'Demo Owner',branch_id:id(2),is_active:true}];
  tables.roles = [{id:id(4),name:'OWNER'}];
  tables.user_roles = [{id:id(5),user_id:id(3),role_id:id(4)}];
  tables.categories = [{id:id(6),name:'Demo category',description:'',is_active:true,created_at:date,updated_at:date}];
  tables.products = [{id:id(7),category_id:id(6),internal_code:'DEMO-01',barcode:null,common_name:'Demo plant',scientific_name:null,
    description:'',price_cents:'200',wholesale_price_cents:null,unit:'maceta',watering_advice:'',light_type:'',recommended_climate:'',
    is_active:true,minimum_stock:2,created_at:date,updated_at:date}];
  tables.sales = [{id:id(8),folio:'DEMO-2026-001',branch_id:id(2),created_by:id(3),status:'PAID',customer_id:null,
    subtotal_cents:'400',discount_cents:'0',total_cents:'400',created_at:date,updated_at:date}];
  tables.sale_items = [{id:id(9),sale_id:id(8),product_id:id(7),product_name:'Demo plant',internal_code:'DEMO-01',quantity:2,
    unit_price_cents:'200',list_price_cents:'200',line_total_cents:'400',discount_cents:'0',promotion_id:null,promotion_name:null,created_at:date}];
  tables.sale_payment_claims = [{id:id(10),sale_id:id(8),branch_id:id(2),cashier_id:id(3),created_at:date,expires_at:'2026-10-01T12:10:00.123456Z',
    consumed_at:date,released_at:null,closed_reason:'CONFIRMED',renewed_at:null,renewal_count:0}];
  tables.sale_payments = [{id:id(11),sale_id:id(8),branch_id:id(2),cashier_id:id(3),claim_id:id(10),method:'CASH',amount_due_cents:'400',
    amount_received_cents:'500',requested_amount_received_cents:'500',change_cents:'100',reference:null,created_at:date}];
  tables.sale_status_history = [[null,'DRAFT'],['DRAFT','SENT_TO_CASHIER'],['SENT_TO_CASHIER','PAID']].map(([previous_status,new_status],i) => ({
    id:id(12+i),sale_id:id(8),previous_status,new_status,changed_by:id(3),observation:'Demo transition',changed_at:date}));
  return {schema_version:1,authority:'supabase-export',exported_at:date,tables,
    primary_keys:SOURCE_TABLES.filter(name => name!=='auth_users').map(table => ({table,columns:['id']})),foreign_keys:[]};
}
