import { createServer } from 'node:http';
import { ApiError, createCatalog, pagination, productFilters, positiveId, validateCategory, validateProduct, scanInput } from './catalog.js';
import { bearerToken, createAuth, requireCapabilities } from './auth/service.js';
import { createImages, createImageUploader, lockImageProduct, validateImagePatch } from './images.js';
import { createPromotions, validatePromotion } from './promotions.js';
import { createWebOrders } from './web-orders.js';
import { createOrderAdmin, orderAdminQuery, orderScope, validateOrderStatus } from './web-order-admin.js';
import { createInventory, inventoryBody, inventoryQuery, createInventoryActivation, activationInput } from './inventory.js';
import { createInventoryObservations, observationInput, decisionInput, observationQuery } from './inventory-observations.js';
import { createProductPreparation } from './product-preparation.js';
import { inventoryPermissions } from './inventory-policy.js';
import { createSales, saleQuery } from './sales.js';
import { createCashier, cashierQuery, claimInput, paymentInput } from './cashier.js';
import { sendOrderToCashier } from './web-order-checkout.js';
import { createRefunds, refundInput, refundFolio } from './refunds.js';
import { createClosings, closingInput } from './closings.js';
import { createCustomers, customerInput, customerQuery } from './customers.js';
import { createAdministration, adminQuery, branchInput, staffInput, accountInput, accountPassword } from './administration.js';
import { createPurchases, supplierInput, presentationInput, draftInput, resolutionInput, purchaseKey, purchaseQuery } from './purchases.js';
import { createReports, reportQuery } from './reports.js';
import { requireBrowserOrigin } from './browser-origin.js';
import { createAccountLinks, accountLinkInput, accountLinkPassword } from './auth/account-links.js';
import { createNewsletter, newsletterInput, newsletterKey } from './newsletter.js';
import { createPasswordChanger } from './auth/change-password.js';
import { shortFolioResponse } from './short-folios.js';
import { createSaleCancellations, cancellationInput } from './sale-cancellations.js';
import { createCataloging, catalogingInput, catalogingKey, catalogingScope, catalogingQuery, productEditVersion, checkProductEditVersion } from './cataloging.js';

async function body(request, maxSize = 16384) {
  if (request.headers['content-type']?.split(';')[0].trim().toLowerCase() !== 'application/json') {
    throw new ApiError(415, 'JSON_REQUIRED');
  }
  let size = 0;
  const chunks = [];
  for await (const chunk of request.iterator({ destroyOnReturn: false })) {
    size += chunk.length;
    if (size > maxSize) {
      request.resume();
      throw new ApiError(413, 'BODY_TOO_LARGE');
    }
    chunks.push(chunk);
  }
  try { return JSON.parse(Buffer.concat(chunks).toString('utf8')); }
  catch { throw new ApiError(400, 'INVALID_JSON'); }
}

export function createApp({ db, auth = createAuth(db), imageStore, webOrigin = null, webOriginAliases = [], accountMailer = null, newsletterConfig = {} }) {
  const changePassword = createPasswordChanger(db, auth);
  const newsletter = createNewsletter(db, newsletterConfig);
  const links = createAccountLinks(db, accountMailer);
  const catalog = createCatalog(db);
  const orders = createWebOrders(db);
  const uploadImage = imageStore ? createImageUploader() : null;
  const server = createServer(async (request, response) => {
    const send = async (status, payload) => {
      if (status < 400 && request.headers['x-vivero-folio-format'] === 'short-v1'
          && !request.url.startsWith('/api/v1/cashier/refunds/lookup')) {
        try { payload = await shortFolioResponse(db, payload); }
        catch { status = 503; payload = { error: 'FOLIO_FORMAT_UNAVAILABLE' }; }
      }
      response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' });
      response.end(JSON.stringify(payload));
    };
    try {
      requireBrowserOrigin(request, webOrigin, webOriginAliases);
      const url = new URL(request.url, 'http://localhost');
      const method = request.method;
      if (url.pathname === '/health' && method === 'GET') {
        try {
          const [migrations] = await db.execute("SELECT version FROM schema_migrations WHERE version = '034_cataloging_drafts'");
          if (!migrations.length) throw new Error('Migration required');
          if (imageStore) await imageStore.check();
        }
        catch { throw new ApiError(503, 'DATABASE_UNAVAILABLE'); }
        return send(200, { status: 'ok', service: 'vivero-api', database: 'mariadb' });
      }
      if (url.pathname === '/api/v1/auth/login' && method === 'POST') {
        return send(200, await auth.login(await body(request), request.socket.remoteAddress));
      }
      const newsletterPublic = /^\/api\/v1\/newsletter\/(subscribe|confirm|unsubscribe)$/.exec(url.pathname);
      if (newsletterPublic) {
        if (method !== 'POST') throw new ApiError(404,'NOT_FOUND');
        if (url.search) throw new ApiError(400,'INVALID_INPUT');
        const action=newsletterPublic[1], input=newsletterInput(await body(request),action);
        return send(action==='subscribe'?202:200,action==='subscribe' ? await newsletter.subscribe(input,request.socket.remoteAddress) : await newsletter[action](input.token));
      }
      const newsletterAdmin = /^\/api\/v1\/admin\/newsletter\/campaigns(?:\/([1-9][0-9]*)\/send)?$/.exec(url.pathname);
      if (newsletterAdmin) {
        bearerToken(request);
        if (url.search) throw new ApiError(400,'INVALID_INPUT');
        if (newsletterAdmin[1]) {
          if (method!=='POST') throw new ApiError(404,'NOT_FOUND');
          const input=await body(request);
          if (!input || typeof input!=='object' || Array.isArray(input) || Object.keys(input).length) throw new ApiError(400,'INVALID_INPUT');
          // Recheck permission before mail configuration/locks and every recipient.
          await auth.withAccess(request,['MANAGE_SETTINGS'],()=>null);
          return send(200,await newsletter.send(request,positiveId(newsletterAdmin[1]),auth));
        }
        if (method==='GET') return send(200,await auth.withAccess(request,['MANAGE_SETTINGS'],(connection,context)=>newsletter.campaigns(connection,context)));
        if (method==='POST') {
          const input=newsletterInput(await body(request,65536),'campaign'), key=newsletterKey(request.headers['idempotency-key']);
          const result=await auth.withAccess(request,['MANAGE_SETTINGS'],(connection,context)=>{
            const expected=request.headers['x-expected-actor-id'];
            if (expected!==undefined && positiveId(expected)!==context.user.id) throw new ApiError(403,'IDENTITY_CHANGED');
            return newsletter.create(connection,context,input,key);
          });
          return send(result.idempotent_replay?200:201,result);
        }
        throw new ApiError(404,'NOT_FOUND');
      }
      if (url.pathname === '/api/v1/auth/change-password') {
        if (method !== 'POST') throw new ApiError(404, 'NOT_FOUND');
        bearerToken(request);
        if (url.search) throw new ApiError(400, 'INVALID_INPUT');
        return send(200, await changePassword(request, await body(request), request.socket.remoteAddress));
      }
      if (['/api/v1/auth/recovery', '/api/v1/auth/password', '/api/v1/admin/staff/invitations'].includes(url.pathname)) {
        if (method !== 'POST') throw new ApiError(404, 'NOT_FOUND');
        if (url.search) throw new ApiError(400, 'INVALID_INPUT');
        if (url.pathname === '/api/v1/auth/recovery') return send(202, await links.request(accountLinkInput(await body(request)), request.socket.remoteAddress));
        if (url.pathname === '/api/v1/auth/password') return send(200, await links.complete(accountLinkPassword(await body(request))));
        bearerToken(request);
        const input = accountLinkInput(await body(request), true);
        const message = await auth.withAccess(request, ['MANAGE_USERS'], (connection, context) => links.invite(connection, context, input), { administration: true });
        await links.deliver(message);
        return send(200, { invited: true });
      }
      const reportRoute = /^\/api\/v1\/reports\/(daily-sales|top-products)$/.exec(url.pathname);
      if (reportRoute) {
        if (method !== 'GET') throw new ApiError(404, 'NOT_FOUND');
        bearerToken(request);
        const top = reportRoute[1] === 'top-products';
        return send(200, await auth.withAccess(request, ['VIEW_REPORTS'], (db, context) => {
          const service = createReports(db, context), query = reportQuery(url.searchParams, top);
          return top ? service.top(query) : service.daily(query);
        }));
      }
      const purchaseRoute = /^\/api\/v1\/(suppliers|supplier-purchases)(?:\/([1-9][0-9]*|retire)(?:\/(presentations|confirm|items)(?:\/([1-9][0-9]*))?)?)?$/.exec(url.pathname);
      if (purchaseRoute) {
        bearerToken(request);
        const [, resource, rawId, action, rawItemId] = purchaseRoute;
        const id = rawId && rawId !== 'retire' ? positiveId(rawId) : null;
        if (rawId === 'retire' && (resource !== 'supplier-purchases' || action || method !== 'POST' || url.search)) throw new ApiError(404, 'NOT_FOUND');
        if (url.search && !(method === 'GET' && !id)) throw new ApiError(400, 'PURCHASE_QUERY_INVALID');
        const result = await auth.withAccess(request, ['MANAGE_INVENTORY'], async (db, context) => {
          for (const [header, actual] of [['x-expected-actor-id', context.user.id], ['x-expected-branch-id', context.branch?.id]]) {
            const expected = request.headers[header];
            if (expected !== undefined && positiveId(expected) !== actual) throw new ApiError(403, 'PURCHASE_IDENTITY_CHANGED');
          }
          const service = createPurchases(db, context);
          if (resource === 'suppliers') {
            if (method === 'GET' && !id) return service.suppliers(purchaseQuery(url.searchParams, true));
            if (method === 'POST' && !id) return service.saveSupplier(null, supplierInput(await body(request)));
            if (method === 'PATCH' && id && !action) return service.saveSupplier(id, supplierInput(await body(request)));
            if (id && action === 'presentations' && !rawItemId) {
              if (method === 'GET') return service.presentations(id);
              if (method === 'PUT') return service.savePresentation(id, presentationInput(await body(request)));
            }
          } else {
            if (rawId === 'retire') {
              const input = await body(request);
              if (!input || typeof input !== 'object' || Array.isArray(input) || Object.keys(input).length) throw new ApiError(400, 'PURCHASE_INPUT_INVALID');
              return service.retireDraft(purchaseKey(request.headers['idempotency-key'], 'draft'));
            }
            if (method === 'GET' && !id) return service.list(purchaseQuery(url.searchParams));
            if (method === 'GET' && id && !action) return service.detail(id);
            if (method === 'POST' && !id) return service.draft(draftInput(await body(request)), purchaseKey(request.headers['idempotency-key'], 'draft'));
            if (method === 'PATCH' && id && action === 'items' && rawItemId) return service.resolve(id, positiveId(rawItemId), resolutionInput(await body(request)));
            if (method === 'POST' && id && action === 'confirm' && !rawItemId) {
              const input = await body(request);
              if (!input || typeof input !== 'object' || Array.isArray(input) || Object.keys(input).length) throw new ApiError(400, 'PURCHASE_INPUT_INVALID');
              return service.confirm(id, purchaseKey(request.headers['idempotency-key'], 'confirmation'));
            }
          }
          throw new ApiError(404, 'NOT_FOUND');
        }, { readCommitted: true });
        return send(method === 'POST' && !result.idempotent_replay ? 201 : 200, result);
      }
      const adminRoute = /^\/api\/v1\/admin\/(branches|staff|roles)(?:\/([1-9][0-9]*)(?:\/(active|role|branch|password))?)?$/.exec(url.pathname);
      if (adminRoute) {
        bearerToken(request);
        const [, resource, rawId, action] = adminRoute;
        const id = rawId ? positiveId(rawId) : null;
        if (url.search && !(method === 'GET' && !id && resource !== 'roles')) throw new ApiError(400, 'ADMIN_QUERY_INVALID');
        const result = await auth.withAccess(request, [], async (db, context) => {
          const service = createAdministration(db, context);
          if (resource === 'staff' && method === 'POST') {
            if (!id) return service.createAccount(accountInput(await body(request)));
            if (action === 'password') return service.resetPassword(id, accountPassword(await body(request)));
          }
          if (method === 'GET' && !id) {
            if (resource === 'roles') return service.roles();
            return resource === 'staff' ? service.staff(adminQuery(url.searchParams, true)) : service.branches(adminQuery(url.searchParams));
          }
          if (resource === 'branches') {
            if (method === 'POST' && !id) return service.createBranch(branchInput(await body(request)));
            if (method === 'PATCH' && id && !action) return service.updateBranch(id, branchInput(await body(request)));
            if (method === 'PATCH' && id && action === 'active') return service.branchActive(id, staffInput('active', await body(request)));
          }
          if (resource === 'staff' && method === 'PATCH' && id && ['role', 'active', 'branch'].includes(action)) return service.changeStaff(id, action, staffInput(action, await body(request)));
          throw new ApiError(404, 'NOT_FOUND');
        }, { readCommitted: true, administration: method !== 'GET' });
        return send(method === 'POST' && !id ? 201 : 200, result);
      }
      const customerRoute = /^\/api\/v1\/customers(?:\/([1-9][0-9]*))?$/.exec(url.pathname);
      if (customerRoute) {
        bearerToken(request);
        const id = customerRoute[1] ? positiveId(customerRoute[1]) : null;
        if (url.search && !(method === 'GET' && !id)) throw new ApiError(400, 'CUSTOMER_QUERY_INVALID');
        const result = await auth.withAccess(request, [], async (db, context) => {
          const service = createCustomers(db, context);
          if (method === 'GET') return id ? service.detail(id) : service.search(customerQuery(url.searchParams));
          if (method === 'POST' && !id) return service.create(customerInput(await body(request)));
          if (method === 'PATCH' && id) return service.update(id, customerInput(await body(request)));
          if (method === 'DELETE' && id) return service.deactivate(id);
          throw new ApiError(404, 'NOT_FOUND');
        }, { readCommitted: true });
        return send(method === 'POST' ? 201 : 200, result);
      }
      const adminOrder = /^\/api\/v1\/admin\/web-orders(?:\/([^/]+))?$/.exec(url.pathname);
      const orderCheckout = /^\/api\/v1\/admin\/web-orders\/([1-9][0-9]*)\/send-to-cashier$/.exec(url.pathname);
      if (orderCheckout && method === 'POST') {
        bearerToken(request);
        if (url.search) throw new ApiError(400, 'INVALID_INPUT');
        const input = await body(request);
        if (!input || Array.isArray(input) || typeof input !== 'object' || Object.keys(input).length) throw new ApiError(400, 'INVALID_INPUT');
        const id = positiveId(orderCheckout[1]);
        const result = await auth.withAccess(request, [], (db, context) => sendOrderToCashier(db, context, id), { readCommitted: true });
        return send(result.idempotent_replay ? 200 : 201, result);
      }
      const receiptsRoute = /^\/api\/v1\/cashier\/receipts(?:\/([1-9][0-9]*))?$/.exec(url.pathname);
      if (receiptsRoute) {
        bearerToken(request);
        if (method !== 'GET') throw new ApiError(404, 'NOT_FOUND');
        const id = receiptsRoute[1] ? positiveId(receiptsRoute[1]) : null;
        if (id && url.search) throw new ApiError(400, 'INVALID_INPUT');
        const query = id ? null : cashierQuery(url.searchParams);
        return send(200, await auth.withAccess(request, ['OPERATE_CASHIER'], (db, context) => {
          const service = createCashier(db, context);
          return id ? service.paymentReceipt(id) : service.receipts(query);
        }, { readCommitted: true }));
      }
      if (url.pathname === '/api/v1/cashier/refunds/lookup') {
        bearerToken(request);
        if (method !== 'GET') throw new ApiError(404, 'NOT_FOUND');
        const folio = refundFolio(url.searchParams);
        return send(200, await auth.withAccess(request, ['OPERATE_CASHIER', 'MANAGE_DISCOUNTS'],
          (db, context) => createRefunds(db, context).lookup(folio), { readCommitted: true }));
      }
      const closingRoute = /^\/api\/v1\/cashier\/closings(?:\/(preview|recover|[1-9][0-9]*))?$/.exec(url.pathname);
      if (closingRoute) {
        bearerToken(request);
        if (url.search) throw new ApiError(400, 'INVALID_INPUT');
        const action = closingRoute[1];
        if (method === 'GET' && action && action !== 'recover') {
          const id = action === 'preview' ? null : positiveId(action);
          return send(200, await auth.withAccess(request, ['OPERATE_CASHIER'], (db, context) => {
            const service = createClosings(db, context);
            return id ? service.detail(id) : service.preview();
          }, { readCommitted: true }));
        }
        if (method === 'POST' && (!action || action === 'recover')) {
          const input = await body(request);
          if (action && (!input || Array.isArray(input) || typeof input !== 'object' || Object.keys(input).length)) throw new ApiError(400, 'CLOSING_DATA_INVALID');
          const data = action ? null : closingInput(input);
          const result = await auth.withAccess(request, ['OPERATE_CASHIER'], (db, context) => {
            const service = createClosings(db, context);
            return action ? service.recover(request.headers['idempotency-key']) : service.close(data, request.headers['idempotency-key']);
          }, { readCommitted: true });
          return send(result.idempotent_replay ? 200 : 201, result);
        }
        throw new ApiError(404, 'NOT_FOUND');
      }
      const refundRoute = /^\/api\/v1\/cashier\/sales\/([1-9][0-9]*)\/refunds$/.exec(url.pathname);
      if (refundRoute && ['GET', 'POST'].includes(method)) {
        bearerToken(request);
        if (url.search) throw new ApiError(400, 'INVALID_INPUT');
        const id = positiveId(refundRoute[1]);
        const input = method === 'POST' ? refundInput(await body(request)) : null;
        const result = await auth.withAccess(request, ['OPERATE_CASHIER', 'MANAGE_DISCOUNTS'], (db, context) => {
          const service = createRefunds(db, context);
          return input ? service.refund(id, input, request.headers['idempotency-key']) : service.preview(id);
        }, { readCommitted: true });
        return send(input && !result.idempotent_replay ? 201 : 200, result);
      }
      const cancellationRoute = /^\/api\/v1\/cashier\/sales\/([1-9][0-9]*)\/(cancel-options|cancel|cancellation-result)$/.exec(url.pathname);
      if (cancellationRoute) {
        if (url.search) throw new ApiError(400, 'INVALID_INPUT');
        const [, rawId, action] = cancellationRoute, id = positiveId(rawId);
        if ((action === 'cancel-options' && method !== 'GET') || (action !== 'cancel-options' && method !== 'POST')) throw new ApiError(404, 'NOT_FOUND');
        const input = method === 'POST' ? await body(request) : null;
        const required = action === 'cancel-options' ? ['OPERATE_CASHIER'] : ['OPERATE_CASHIER', 'MANAGE_DISCOUNTS'];
        const result = await auth.withAccess(request, required, (connection, context) => {
          for (const [header, actual] of [['x-expected-actor-id', context.user.id], ['x-expected-branch-id', context.branch?.id]]) {
            const expected = request.headers[header];
            if (expected !== undefined && positiveId(expected) !== actual) throw new ApiError(403, 'CASHIER_IDENTITY_CHANGED');
          }
          const service = createSaleCancellations(connection, context);
          if (action === 'cancel-options') return service.options(id);
          if (action === 'cancel') return service.cancel(id, cancellationInput(input), request.headers['idempotency-key']);
          if (!input || typeof input !== 'object' || Array.isArray(input) || Object.keys(input).length) throw new ApiError(400, 'INVALID_INPUT');
          return service.recover(id, request.headers['idempotency-key']);
        }, { readCommitted: true });
        return send(action === 'cancel' && !result.idempotent_replay ? 201 : 200, result);
      }
      const cashierRoute = /^\/api\/v1\/cashier\/sales(?:\/([1-9][0-9]*)(?:\/(claim|release|payments|payment-result|payment-retire))?)?$/.exec(url.pathname);
      if (cashierRoute) {
        bearerToken(request);
        const [, saleId, action] = cashierRoute;
        if (method === 'GET' && !saleId) {
          const query = { ...cashierQuery(url.searchParams), desk: request.headers['x-vivero-cashier-view'] === 'desk-v1' };
          return send(200, await auth.withAccess(request, ['OPERATE_CASHIER'], (db, context) => createCashier(db, context).list(query), { readCommitted: true }));
        }
        const id = saleId ? positiveId(saleId) : null;
        if (method === 'GET' && id && !action) {
          if ([...url.searchParams.keys()].some(key => key !== 'view')) throw new ApiError(400, 'INVALID_INPUT');
          const { operations } = cashierQuery(url.searchParams);
          return send(200, await auth.withAccess(request, ['OPERATE_CASHIER'], (db, context) => createCashier(db, context).detail(id, operations), { readCommitted: true }));
        }
        if (url.search) throw new ApiError(400, 'INVALID_INPUT');
        if (method === 'POST' && id && action) {
          const input = await body(request);
          const result = await auth.withAccess(request, ['OPERATE_CASHIER'], (db, context) => {
            for (const [header, actual] of [['x-expected-actor-id', context.user.id], ['x-expected-branch-id', context.branch?.id]]) {
              const expected = request.headers[header];
              if (expected !== undefined && positiveId(expected) !== actual) throw new ApiError(403, 'CASHIER_IDENTITY_CHANGED');
            }
            const service = createCashier(db, context);
            if (action === 'claim') return service.claim(id, claimInput(input, true));
            if (action === 'release') return service.release(id, claimInput(input));
            if (action === 'payments') return service.pay(id, paymentInput(input), request.headers['idempotency-key']);
            if (!input || Array.isArray(input) || typeof input !== 'object' || Object.keys(input).length) throw new ApiError(400, 'PAYMENT_DATA_INVALID');
            if (action === 'payment-retire') return service.retire(id, request.headers['idempotency-key']);
            return service.recover(id, request.headers['idempotency-key']);
          }, { readCommitted: true });
          return send(action === 'payments' && !result.idempotent_replay ? 201 : 200, result);
        }
        throw new ApiError(404, 'NOT_FOUND');
      }
      const saleRoute = /^\/api\/v1\/sales(?:\/(quote|recover|retire|[1-9][0-9]*))?$/.exec(url.pathname);
      if (saleRoute) {
        bearerToken(request);
        const checkSaleIdentity = context => {
          for (const [header, actual] of [['x-expected-actor-id', context.user.id], ['x-expected-branch-id', context.branch?.id]]) {
            const expected = request.headers[header];
            if (expected !== undefined && positiveId(expected) !== actual) throw new ApiError(403, 'SALE_IDENTITY_CHANGED');
          }
        };
        if (method === 'GET' && !saleRoute[1]) {
          const query = saleQuery(url.searchParams);
          return send(200, await auth.withAccess(request, ['CREATE_SALES', 'VIEW_OWN_SALES'], (db, context) => createSales(db, context).list(query)));
        }
        if (url.search) throw new ApiError(400, 'SALE_QUERY_INVALID');
        if (saleRoute[1] === 'quote') {
          if (method !== 'POST') throw new ApiError(404, 'NOT_FOUND');
          const input = await body(request);
          return send(200, await auth.withAccess(request, ['CREATE_SALES'], (db, context) => {
            checkSaleIdentity(context);
            return createSales(db, context).quote(input);
          }, { readCommitted: true }));
        }
        if (method === 'GET' && !['recover', 'retire'].includes(saleRoute[1]) && saleRoute[1]) {
          const id = positiveId(saleRoute[1]);
          return send(200, await auth.withAccess(request, ['CREATE_SALES', 'VIEW_OWN_SALES'], (db, context) => createSales(db, context).detail(id)));
        }
        if (method === 'POST' && (!saleRoute[1] || ['recover', 'retire'].includes(saleRoute[1]))) {
          const input = await body(request);
          if (saleRoute[1] && (!input || Array.isArray(input) || typeof input !== 'object' || Object.keys(input).length)) throw new ApiError(400, 'SALE_INPUT_INVALID');
          const result = await auth.withAccess(request, ['CREATE_SALES'], (db, context) => {
            checkSaleIdentity(context);
            const service = createSales(db, context);
            if (saleRoute[1] === 'retire') return service.retire(request.headers['idempotency-key']);
            return saleRoute[1] ? service.recover(request.headers['idempotency-key']) : service.submit(input, request.headers['idempotency-key']);
          }, { readCommitted: true });
          return send(saleRoute[1] === 'retire' || result.idempotent_replay ? 200 : 201, result);
        }
        throw new ApiError(404, 'NOT_FOUND');
      }
      if (adminOrder) {
        bearerToken(request);
        return send(200, await auth.withAccess(request, [], async (connection, context) => {
          orderScope(context, method === 'PATCH');
          const service = createOrderAdmin(connection, context);
          if (method === 'GET' && !adminOrder[1]) return service.list(orderAdminQuery(url.searchParams));
          if (method === 'GET' && adminOrder[1] && url.searchParams.get('view') === 'operations'
            && [...url.searchParams.keys()].length === 1) return service.detail(positiveId(adminOrder[1]), true);
          if (url.search) throw new ApiError(400, 'INVALID_INPUT');
          const id = adminOrder[1] ? positiveId(adminOrder[1]) : null;
          if (method === 'GET' && id) return service.detail(id);
          if (method === 'PATCH' && id) return service.update(id, validateOrderStatus(await body(request)));
          throw new ApiError(404, 'NOT_FOUND');
          }, { readCommitted: true }));
      }
      if (url.pathname === '/api/v1/inventory/permissions' && method === 'GET') {
        if (url.search) throw new ApiError(400, 'INVENTORY_QUERY_INVALID');
        return send(200, await auth.withAccess(request, ['MANAGE_INVENTORY'], (_db, context) =>
          ({ schema_version: 1, branch_id: context.branch?.id, permissions: inventoryPermissions(context) })));
      }
      if (url.pathname === '/api/v1/inventory/count-baseline' && method === 'GET') {
        if ([...url.searchParams.keys()].length !== 1 || !url.searchParams.has('product_id')) throw new ApiError(400, 'INVENTORY_QUERY_INVALID');
        const id = positiveId(url.searchParams.get('product_id'));
        return send(200, await auth.withAccess(request, ['MANAGE_INVENTORY'], (db, context) =>
          createInventoryObservations(db, context).baseline(id), { readCommitted: true }));
      }
      const observations = /^\/api\/v1\/inventory\/count-observations(?:\/(result|[1-9][0-9]*)\/?)?$/.exec(url.pathname);
      if (observations) {
        bearerToken(request);
        const operation = observations[1];
        if (method !== 'GET' && url.search) throw new ApiError(400, 'INVENTORY_QUERY_INVALID');
        if (method === 'GET' && operation) throw new ApiError(404, 'NOT_FOUND');
        const query = method === 'GET' ? observationQuery(url.searchParams) : null;
        const input = method === 'POST' ? (operation && operation !== 'result'
          ? decisionInput(await body(request), request.headers['idempotency-key'])
          : observationInput(await body(request), request.headers['idempotency-key'])) : null;
        if (!['GET', 'POST'].includes(method)) throw new ApiError(404, 'NOT_FOUND');
        const result = await auth.withAccess(request, ['MANAGE_INVENTORY'], (db, context) => {
          for (const [header, actual] of [['x-expected-actor-id', context.user.id], ['x-expected-branch-id', context.branch?.id]]) {
            if (request.headers[header] !== undefined && positiveId(request.headers[header]) !== actual) throw new ApiError(403, 'INVENTORY_IDENTITY_CHANGED');
          }
          const service = createInventoryObservations(db, context);
          if (query) return service.list(query);
          if (operation === 'result') return service.result(input);
          return operation ? service.decide(positiveId(operation), input) : service.observe(input);
        }, { readCommitted: true });
        return send(method === 'GET' || result.idempotent_replay ? 200 : 201, result);
      }
      const preparation = /^\/api\/v1\/products\/([1-9][0-9]*)\/preparation(?:\/(activate))?$/.exec(url.pathname);
      if (preparation) {
        bearerToken(request);
        if (url.search || (preparation[2] ? method !== 'POST' : method !== 'GET')) throw new ApiError(404, 'NOT_FOUND');
        if (method === 'POST') {
          const input = await body(request);
          if (!input || typeof input !== 'object' || Array.isArray(input) || Object.keys(input).length) throw new ApiError(400, 'INVALID_INPUT');
        }
        return send(200, await auth.withAccess(request, ['MANAGE_PRODUCTS'], (db, context) => {
          for (const [header, actual] of [['x-expected-actor-id', context.user.id], ['x-expected-branch-id', context.branch?.id]]) {
            if (request.headers[header] !== undefined && positiveId(request.headers[header]) !== actual) throw new ApiError(403, 'INVENTORY_IDENTITY_CHANGED');
          }
          const service = createProductPreparation(db, context);
          return preparation[2] ? service.activate(positiveId(preparation[1])) : service.state(positiveId(preparation[1]));
        }, { readCommitted: true }));
      }
      if (url.pathname === '/api/v1/inventory/activation' && ['GET', 'POST'].includes(method)) {
        bearerToken(request);
        if (url.search) throw new ApiError(400, 'INVENTORY_QUERY_INVALID');
        if (method === 'POST') activationInput(await body(request));
        return send(200, await auth.withAccess(request, ['MANAGE_INVENTORY'], (db, context) => {
          for (const [header, actual] of [['x-expected-actor-id', context.user.id], ['x-expected-branch-id', context.branch?.id]]) {
            if (request.headers[header] !== undefined && positiveId(request.headers[header]) !== actual) throw new ApiError(403, 'INVENTORY_IDENTITY_CHANGED');
          }
          const service = createInventoryActivation(db, context);
          return method === 'POST' ? service.activate() : service.state();
        }, { readCommitted: true }));
      }
      const inventory = /^\/api\/v1\/inventory\/(dashboard|history|receptions|counts)(\/result)?$/.exec(url.pathname);
      if (inventory) {
        bearerToken(request);
        const [, operation, recovery] = inventory;
        if (recovery && (!['receptions', 'counts'].includes(operation) || method !== 'POST')) throw new ApiError(404, 'NOT_FOUND');
        if (url.search && method !== 'GET') throw new ApiError(400, 'INVENTORY_QUERY_INVALID');
        if (method === 'GET' && ['dashboard', 'history'].includes(operation)) {
          const query = inventoryQuery(url.searchParams, operation === 'history');
          const result = await auth.withAccess(request, [], (connection, context) => {
            const inventoryService = createInventory(connection, context);
            return operation === 'dashboard' ? inventoryService.dashboard(query) : inventoryService.history(query);
          });
          return send(200, result);
        }
        if (method === 'POST' && ['receptions', 'counts'].includes(operation)) {
          if (url.search) throw new ApiError(400, 'INVENTORY_QUERY_INVALID');
          const input = inventoryBody(operation === 'receptions' ? 'reception' : 'count', await body(request), request.headers['idempotency-key']);
          const result = await auth.withAccess(request, ['MANAGE_INVENTORY'], (connection, context) => {
            for (const [header, actual] of [['x-expected-actor-id', context.user.id], ['x-expected-branch-id', context.branch?.id]]) {
              const expected = request.headers[header];
              if (expected !== undefined && positiveId(expected) !== actual) throw new ApiError(403, 'INVENTORY_IDENTITY_CHANGED');
            }
            const inventoryService = createInventory(connection, context);
            if (recovery) return inventoryService.result(input, operation);
            return operation === 'receptions' ? inventoryService.reception(input) : inventoryService.reconcile(input);
          });
          return send(result.idempotent_replay ? 200 : 201, result);
        }
        throw new ApiError(404, 'NOT_FOUND');
      }
      if (url.pathname.startsWith('/api/v1/web-orders')) {
        if (url.search) throw new ApiError(400, 'INVALID_INPUT');
        if (method === 'GET' && url.pathname === '/api/v1/web-orders/options') return send(200, await orders.options());
        if (method === 'POST' && url.pathname === '/api/v1/web-orders/quote') return send(200, await orders.quote(await body(request)));
        if (method === 'POST' && ['/api/v1/web-orders/recover', '/api/v1/web-orders/ticket'].includes(url.pathname)) {
          const input = await body(request);
          if (!input || typeof input !== 'object' || Array.isArray(input) || Object.keys(input).length) throw new ApiError(400, 'INVALID_INPUT');
          return send(200, await (url.pathname.endsWith('/ticket') ? orders.ticket(request.headers['idempotency-key']) : orders.recover(request.headers['idempotency-key'])));
        }
        if (method === 'POST' && url.pathname === '/api/v1/web-orders') {
          const result = await orders.submit(await body(request), request.headers['idempotency-key']);
          return send(result.idempotent_replay ? 200 : 201, result);
        }
        throw new ApiError(404, 'NOT_FOUND');
      }
      if (url.pathname === '/api/v1/auth/me' && method === 'GET') {
        return send(200, await auth.withAccess(request, [], async (_connection, context) => context));
      }
      if (url.pathname === '/api/v1/auth/logout' && method === 'POST') {
        return send(200, await auth.logout(request));
      }
      if (url.pathname === '/api/v1/products/scan') {
        bearerToken(request);
        if (method !== 'POST') throw new ApiError(404, 'NOT_FOUND');
        if (url.search) throw new ApiError(400, 'PRODUCT_SCAN_CODE_INVALID');
        const input = await body(request);
        return send(200, await auth.withAccess(request, ['VIEW_CATALOG'], connection => createCatalog(connection).scan(scanInput(input))));
      }
      const promotion = /^\/api\/v1\/promotions(?:\/([^/]+))?$/.exec(url.pathname);
      if (promotion) {
        bearerToken(request);
        if (method === 'GET' && !promotion[1]) {
          if ([...url.searchParams.keys()].some(key => !['limit', 'after_id'].includes(key))) throw new ApiError(400, 'INVALID_INPUT');
          const page = pagination(url.searchParams);
          return send(200, await auth.withAccess(request, ['MANAGE_DISCOUNTS'], connection => createPromotions(connection).list(page)));
        }
        if (url.search) throw new ApiError(400, 'INVALID_INPUT');
        if ((method === 'POST' && !promotion[1]) || (method === 'PUT' && promotion[1])) {
          const id = promotion[1] ? positiveId(promotion[1]) : null;
          const data = validatePromotion(await body(request));
          return send(id === null ? 201 : 200, await auth.withAccess(request, ['MANAGE_DISCOUNTS'], connection => createPromotions(connection).save(id, data)));
        }
        if (method === 'DELETE' && promotion[1]) {
          const id = positiveId(promotion[1]);
          return send(200, await auth.withAccess(request, ['MANAGE_DISCOUNTS'], connection => createPromotions(connection).deactivate(id)));
        }
        throw new ApiError(404, 'NOT_FOUND');
      }
      const draftRoute = /^\/api\/v1\/cataloging(?:\/([1-9][0-9]*)(?:\/(photo))?)?$/.exec(url.pathname);
      if (draftRoute) {
        bearerToken(request);
        const id = draftRoute[1] ? positiveId(draftRoute[1]) : null;
        const action = fn => auth.withAccess(request, ['MANAGE_PRODUCTS'], (connection, context) => {
          catalogingScope(context, request);
          return fn(createCataloging(connection,context));
        });
        if (!id && method === 'GET') return send(200,await action(service => service.list(catalogingQuery(url.searchParams))));
        if (url.search) throw new ApiError(400,'CATALOGING_INPUT_INVALID');
        if (id && draftRoute[2]) {
          if (!imageStore) throw new ApiError(503,'IMAGE_STORAGE_UNAVAILABLE');
          if (method === 'GET') {
            const key=await action(service=>service.photoKey(id)), bytes=await imageStore.get(key);
            response.writeHead(200,{'Content-Type':'image/webp','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});
            return response.end(bytes);
          }
          if (method === 'PUT') {
            const revision=positiveId(request.headers['x-cataloging-revision']);
            const key=catalogingKey(request.headers['idempotency-key']);
            await action(service=>service.detail(id));
            const uploaded=await uploadImage(request);
            return send(200,await action(service=>service.photo(id,revision,key,imageStore,uploaded)));
          }
        }
        if (id && method === 'GET') return send(200,await action(service=>service.detail(id)));
        if ((!id && method==='POST') || (id && method==='PATCH')) {
          const key=catalogingKey(request.headers['idempotency-key']), input=catalogingInput(await body(request),!id);
          return send(id ? 200 : 201,await action(service=>id ? service.update(id,input,key) : service.create(input,key)));
        }
        throw new ApiError(404,'NOT_FOUND');
      }
      const editVersion = /^\/api\/v1\/products\/([1-9][0-9]*)\/edit-version$/.exec(url.pathname);
      if (editVersion && method==='GET') {
        if(url.search)throw new ApiError(400,'INVALID_INPUT');
        return send(200,await auth.withAccess(request,['MANAGE_PRODUCTS'],connection=>productEditVersion(connection,positiveId(editVersion[1]))));
      }
      const guardedPhoto = /^\/api\/v1\/products\/([1-9][0-9]*)\/catalog-photo$/.exec(url.pathname);
      if (guardedPhoto && method==='PUT') {
        if(!imageStore)throw new ApiError(503,'IMAGE_STORAGE_UNAVAILABLE');
        if(!request.headers['if-match'])throw new ApiError(428,'CATALOGING_VERSION_REQUIRED');
        const id=positiveId(guardedPhoto[1]);
        await auth.withAccess(request,['MANAGE_PRODUCTS'],connection=>checkProductEditVersion(connection,id,request.headers['if-match']));
        const uploaded=await uploadImage(request);
        return send(200,await auth.withAccess(request,['MANAGE_PRODUCTS'],async connection=>{
          await checkProductEditVersion(connection,id,request.headers['if-match']);
          const images=createImages(connection), result=await images.add(id,imageStore,uploaded.data,uploaded.info);
          await images.update(id,result.id,{is_primary:true});
          return result;
        }));
      }
      const imageCollection = /^\/api\/v1\/products\/([^/]+)\/images$/.exec(url.pathname);
      const imageItem = /^\/api\/v1\/products\/([^/]+)\/images\/([^/]+)$/.exec(url.pathname);
      const imageFile = /^\/api\/v1\/images\/([^/]+)$/.exec(url.pathname);
      if (imageCollection || imageItem || imageFile) {
        if (!imageStore) throw new ApiError(503, 'IMAGE_STORAGE_UNAVAILABLE');
        if (method === 'GET' && (imageCollection || imageFile)) {
          if ([...url.searchParams.keys()].some(key => key !== 'status') || !['active', 'all'].includes(url.searchParams.get('status') ?? 'active')) throw new ApiError(400, 'INVALID_INPUT');
          const admin = url.searchParams.get('status') === 'all';
          const id = positiveId((imageCollection || imageFile)[1]);
          const action = async connection => imageCollection ? createImages(connection).list(id, admin) : createImages(connection).file(id, admin);
          const result = admin ? await auth.withAccess(request, ['MANAGE_PRODUCTS'], action) : await action(db);
          if (imageCollection) return send(200, result);
          const bytes = await imageStore.get(result);
          response.writeHead(200, { 'Content-Type': 'image/webp', 'Content-Length': bytes.length, 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff', 'Content-Security-Policy': "default-src 'none'" });
          return response.end(bytes);
        }
        if (url.search) throw new ApiError(400, 'INVALID_INPUT');
        if (imageCollection && method === 'POST') {
          const productId = positiveId(imageCollection[1]);
          // Check access before receiving bytes; recheck before committing metadata.
          await auth.withAccess(request, ['MANAGE_PRODUCTS'], connection => lockImageProduct(connection, productId));
          const uploaded = await uploadImage(request);
          return send(201, await auth.withAccess(request, ['MANAGE_PRODUCTS'], connection =>
            createImages(connection).add(productId, imageStore, uploaded.data, uploaded.info)));
        }
        if (imageItem && ['PATCH', 'DELETE'].includes(method)) {
          bearerToken(request);
          const productId = positiveId(imageItem[1]);
          const id = positiveId(imageItem[2]);
          const data = method === 'DELETE' ? {} : validateImagePatch(await body(request));
          return send(200, await auth.withAccess(request, ['MANAGE_PRODUCTS'], connection =>
            createImages(connection).update(productId, id, data, method === 'DELETE')));
        }
        throw new ApiError(404, 'NOT_FOUND');
      }
      const collection = /^\/api\/v1\/(products|categories)$/.exec(url.pathname);
      if (collection && method === 'GET') {
        const keys = collection[1] === 'products' ? ['limit', 'after_id', 'status', 'search', 'category_id'] : ['limit', 'after_id', 'status'];
        if ([...url.searchParams.keys()].some(key => !keys.includes(key) || url.searchParams.getAll(key).length !== 1)) throw new ApiError(400, 'INVALID_INPUT');
        const status = url.searchParams.get('status') ?? 'active';
        if (!['active', 'all'].includes(status)) throw new ApiError(400, 'INVALID_INPUT');
        const page = pagination(url.searchParams);
        if (collection[1] === 'products') Object.assign(page, productFilters(url.searchParams));
        if (status === 'all') {
          return send(200, await auth.withAccess(request, ['MANAGE_PRODUCTS'], (connection, context) =>
            createCatalog(connection, context).list(collection[1], page, true)));
        }
        return send(200, await catalog.list(collection[1], page, false));
      }
      if (collection && method === 'POST') {
        bearerToken(request);
        const input = await body(request);
        const data = collection[1] === 'products' ? validateProduct(input) : validateCategory(input);
        const permissions = collection[1] === 'products' ? ['MANAGE_PRODUCTS', 'MANAGE_PRICES'] : ['MANAGE_PRODUCTS'];
        return send(201, await auth.withAccess(request, permissions, (connection, context) => createCatalog(connection, context).create(collection[1], data)));
      }
      const category = /^\/api\/v1\/categories\/([^/]+)$/.exec(url.pathname);
      if (category && ['PATCH', 'DELETE'].includes(method)) {
        bearerToken(request);
        const id = positiveId(category[1]);
        const data = method === 'DELETE' ? { is_active: false } : validateCategory(await body(request), true);
        return send(200, await auth.withAccess(request, ['MANAGE_PRODUCTS'], connection =>
          createCatalog(connection).update(id, data, 'categories')));
      }
      const product = /^\/api\/v1\/products\/([^/]+)$/.exec(url.pathname);
      if (product && ['PATCH', 'DELETE'].includes(method)) {
        bearerToken(request);
        const id = positiveId(product[1]);
        const data = method === 'DELETE' ? { is_active: false } : validateProduct(await body(request), true);
        return send(200, await auth.withAccess(request, ['MANAGE_PRODUCTS'], async (connection, context) => {
          await checkProductEditVersion(connection,id,request.headers['if-match']);
          // INVENTORY can save unchanged prices, as in the existing Supabase RPC.
          if (Object.hasOwn(data, 'price_cents') || Object.hasOwn(data, 'wholesale_price_cents')) {
            const [[current]] = await connection.execute('SELECT price_cents, wholesale_price_cents FROM products WHERE id = ? FOR UPDATE', [id]);
            if (!current) throw new ApiError(404, 'NOT_FOUND');
            for (const key of ['price_cents', 'wholesale_price_cents']) {
              const previous = current[key] === null ? null : Number(current[key]);
              if (Object.hasOwn(data, key) && previous !== data[key]) requireCapabilities(context, ['MANAGE_PRICES']);
            }
          }
          return createCatalog(connection, context).update(id, data);
        }));
      }
      throw new ApiError(404, 'NOT_FOUND');
    } catch (error) {
      if (error instanceof ApiError) return send(error.status, { error: error.code });
      if (error.code === 'ER_DUP_ENTRY') return send(409, { error: 'DUPLICATE' });
      if (['ER_NO_REFERENCED_ROW_2', 'ER_ROW_IS_REFERENCED_2'].includes(error.code)) return send(409, { error: 'REFERENCE_CONFLICT' });
      if (error.errno === 4025) return send(400, { error: 'INVALID_INPUT' });
      // Never log SQL, credentials, request bodies or driver error messages.
      console.error('Request failed');
      return send(503, { error: 'SERVICE_UNAVAILABLE' });
    }
  });
  server.requestTimeout = 10000;
  server.headersTimeout = 10000;
  server.setTimeout(15000, socket => socket.destroy());
  return server;
}
