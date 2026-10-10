// Offline recovery verification. No SSH, production endpoints or migration 033.
import {readFile,writeFile,mkdir,readdir,copyFile,stat} from 'node:fs/promises';
import {createReadStream,createWriteStream,openSync,readSync,closeSync} from 'node:fs';
import {createHash,createDecipheriv} from 'node:crypto';
import {pipeline} from 'node:stream/promises';
import {execFileSync} from 'node:child_process';
import {resolve,join,relative,basename} from 'node:path';
const opts={};for(let i=2;i<process.argv.length;i+=2)opts[process.argv[i]]=process.argv[i+1];
const fail=code=>{throw Error(code)};
const sha=async path=>{const h=createHash('sha256');for await(const c of createReadStream(path))h.update(c);return h.digest('hex')};
const docker=(args,input)=>{try{return execFileSync('docker',args,{input,stdio:['pipe','pipe','pipe'],windowsHide:true,timeout:180000,maxBuffer:16*1024*1024}).toString().trim()}catch{fail('LOCAL_DOCKER_COMMAND_FAILED_NO_PRIVATE_OUTPUT')}};
const tar=args=>{try{return execFileSync('tar',args,{stdio:['ignore','pipe','pipe'],windowsHide:true,maxBuffer:16*1024*1024}).toString().trim()}catch{fail('ARCHIVE_COMMAND_FAILED_NO_PRIVATE_OUTPUT')}};
const quote=s=>"'"+s.replaceAll('\\','\\\\').replaceAll("'","''")+"'";
const normalized=lines=>lines.map(l=>l.replace(/\((`[^)]+`)\)/g,(_,c)=>'('+c.split(',').map(c=>c.trim()).sort().join(',')+')')).sort();
const identifier=s=>{if(!/^[a-z][a-z0-9_]*$/.test(s))fail('UNSAFE_SQL_IDENTIFIER');return '`'+s+'`'};
const childPath=(parent,name)=>{const p=resolve(parent,name),rel=relative(parent,p);if(rel.startsWith('..')||resolve(p)===resolve(parent))fail('UNSAFE_RECOVERY_PATH');return p};
function safeExtract(archive,destination,allowedRoots){
 const names=tar(['-tzf',archive]).split(/\r?\n/);
 if(names.some(n=>n.startsWith('/')||n.includes('\\')||n.split('/').includes('..')||!allowedRoots.includes(n.split('/')[0])))fail('UNSAFE_ARCHIVE_PATH');
 if(tar(['-tvzf',archive]).split(/\r?\n/).some(n=>!/^[-d]/.test(n)))fail('ARCHIVE_LINKS_NOT_ALLOWED');
 tar(['-xzf',archive,'-C',destination]);
}
let compose,container;
try{
 for(const k of ['--encrypted','--key-file','--workdir','--project','--archive-sha256','--encrypted-sha256'])if(!opts[k])fail('RECOVERY_ARGUMENTS_REQUIRED');
 const encrypted=resolve(opts['--encrypted']),keyFile=resolve(opts['--key-file']),work=resolve(opts['--workdir']),project=opts['--project'];
 if(!/^vivero-restore-[a-z0-9-]{1,40}$/.test(project))fail('ISOLATED_PROJECT_NAME_REQUIRED');
 if([encrypted,keyFile].some(p=>!relative(work,p).startsWith('..')))fail('INPUTS_MUST_REMAIN_OUTSIDE_WORKDIR');
 const endpoint=process.env.DOCKER_HOST||JSON.parse(docker(['context','inspect','--format','{{json .Endpoints.docker.Host}}']));
 if(!/^(unix:\/\/|npipe:\/\/)/.test(endpoint))fail('LOCAL_DOCKER_REQUIRED');
 if(docker(['ps','-aq','--filter',`label=com.docker.compose.project=${project}`])||docker(['volume','ls','-q','--filter',`label=com.docker.compose.project=${project}`]))fail('ISOLATED_PROJECT_ALREADY_EXISTS');
 if(await sha(encrypted)!==opts['--encrypted-sha256'])fail('ENCRYPTED_HASH_DIFFERS');
 await mkdir(work); // Refuse to reuse or clear an existing workspace.
 const keyCommand="Add-Type -AssemblyName System.Security.Cryptography.ProtectedData; $recoveryKey=[System.Security.Cryptography.ProtectedData]::Unprotect([System.IO.File]::ReadAllBytes($env:VIVERO_RECOVERY_KEY_FILE),$null,[System.Security.Cryptography.DataProtectionScope]::CurrentUser); [Convert]::ToBase64String($recoveryKey); [System.Array]::Clear($recoveryKey,0,$recoveryKey.Length)";
 const encoded=execFileSync('pwsh',['-NoProfile','-Command',keyCommand],{env:{...process.env,VIVERO_RECOVERY_KEY_FILE:keyFile},stdio:['ignore','pipe','pipe'],windowsHide:true});
 const key=Buffer.from(encoded.toString().trim(),'base64');encoded.fill(0);if(key.length!==32)fail('RECOVERY_KEY_INVALID');
 const archive=join(work,'authenticated-backup.tar.gz'),size=(await stat(encrypted)).size,header=Buffer.alloc(20),tag=Buffer.alloc(16),fd=openSync(encrypted,'r');
 try{readSync(fd,header,0,20,0);readSync(fd,tag,0,16,size-16)}finally{closeSync(fd)}
 if(!header.subarray(0,8).equals(Buffer.from('VIVEROP1')))fail('ENCRYPTED_FORMAT_INVALID');
 try{
  const decipher=createDecipheriv('aes-256-gcm',key,header.subarray(8));decipher.setAAD(Buffer.from('Vivero Dulcinea pilot backup AES-256-GCM v1'));decipher.setAuthTag(tag);
  await pipeline(createReadStream(encrypted,{start:20,end:size-17}),decipher,createWriteStream(archive,{flags:'wx',mode:0o600}));
 }finally{key.fill(0)}
 if(await sha(archive)!==opts['--archive-sha256'])fail('AUTHENTICATED_ARCHIVE_HASH_DIFFERS');
 const roots=[...new Set(tar(['-tzf',archive]).split(/\r?\n/).map(n=>n.split('/')[0]))];if(roots.length!==1||!/^vivero-[a-zA-Z0-9-]+$/.test(roots[0]))fail('BACKUP_ROOT_INVALID');
 const extraction=join(work,'backup');await mkdir(extraction);safeExtract(archive,extraction,roots);
 const directory=join(extraction,roots[0]),recovery=join(directory,'recovery'),full=JSON.parse(await readFile(join(directory,'recovery-manifest.json')));
 for(const [name,h]of Object.entries(full.files))if(await sha(childPath(directory,name))!==h)fail('RECOVERY_FILE_HASH_DIFFERS');
 const sources=join(work,'sources');await mkdir(sources);safeExtract(join(recovery,'release-source.tar.gz'),sources,['ViveroApp','ViveroWeb']);
 const source=join(sources,'ViveroApp'),schema=join(source,'database/mysql'),infra=join(source,'infra/docker'),initial=join(work,'migrations032');await mkdir(initial);
 const snapshot=JSON.parse(await readFile(join(recovery,'integrity.private.json')));
 if(snapshot.migrations.at(-1)!=='032_pending_sale_cancellations')fail('ONLY_SCHEMA_032_SUPPORTED');
 for(const name of (await readdir(join(schema,'migrations'))).filter(n=>/^\d{3}_.*\.sql$/.test(n)&&n<'033_'))await copyFile(join(schema,'migrations',name),join(initial,name));
 const runtime=JSON.parse(await readFile(join(recovery,'runtime-metadata.json'))),dbImage=runtime['vivero-vps-db-1'].image,apiImage=runtime['vivero-vps-api-1'].image;
 docker(['image','load','--input',join(recovery,'api-web-images.tar')]);
 for(const im of [dbImage,apiImage])docker(['image','inspect',im]);
 const mount=(p,target)=>JSON.stringify(p.replaceAll('\\','/')+':'+target+':ro'),overlay=join(work,'isolated.yaml');
 await writeFile(overlay,`services:
  db:
    image: ${dbImage}
    restart: "no"
    ports: !reset []
    volumes: !override
      - mariadb_data:/var/lib/mysql
      - ${mount(join(schema,'schema.sql'),'/docker-entrypoint-initdb.d/01-schema.sql')}
      - ${mount(join(schema,'seed-production.sql'),'/docker-entrypoint-initdb.d/02-seed.sql')}
      - ${mount(join(schema,'grants.sql'),'/docker-entrypoint-initdb.d/03-grants.sql')}
      - ${mount(initial,'/database/migrations')}
      - ${mount(join(infra,'init-migrations.sh'),'/docker-entrypoint-initdb.d/04-migrations.sh')}
  api:
    image: ${apiImage}
    restart: "no"
    ports: !reset []
    networks: !override [api, database]
    environment:
      RESEND_API_KEY: ""
      ACCOUNT_MAIL_FROM: ""
      NEWSLETTER_FROM: ""
      NEWSLETTER_LINK_KEY: ""
  web:
    restart: "no"
    ports: !reset []
networks:
  api:
    internal: true
  database:
    internal: true
`);
 compose=['compose','--env-file',join(recovery,'service.env.private'),'-p',project,'-f',join(infra,'compose.yaml'),'-f',join(infra,'compose.vps.yaml'),'-f',overlay];
 const call=(args,input)=>docker([...compose,...args],input);
 const cfg=JSON.parse(call(['config','--format','json']));if(Object.values(cfg.networks).some(n=>!n.internal||n.external)||cfg.services.db.ports?.length||cfg.services.api.ports?.length)fail('ISOLATION_CONFIGURATION_FAILED');
 call(['up','-d','--no-deps','--no-build','--pull','never','db']);container=project+'-db-1';
 let healthy=false;for(let i=0;i<90;i++){if(docker(['inspect',container,'--format','{{.State.Health.Status}}'])==='healthy'){healthy=true;break}await new Promise(r=>setTimeout(r,1000))}if(!healthy)fail('ISOLATED_DATABASE_UNHEALTHY');
 const query=sql=>docker(['exec',container,'sh','-c','export MYSQL_PWD="$MARIADB_ROOT_PASSWORD"; exec mariadb --user=root --batch --raw --skip-column-names --execute="$1"','offline-recovery',sql]);
 const reference=new Set(['roles','permissions','role_permissions','schema_migrations','sale_folio_counter']);
 const tables=query("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='vivero' ORDER BY TABLE_NAME").split('\n');
 for(const t of tables.filter(t=>!reference.has(t)))if(query('SELECT COUNT(*) FROM vivero.'+identifier(t))!=='0')fail('TARGET_NOT_EMPTY');
 if(query('SELECT IF(COUNT(*)=1 AND MIN(id)=1 AND MIN(last_value)=0 AND MAX(last_value)=0,1,0) FROM vivero.sale_folio_counter')!=='1')fail('TARGET_FOLIO_COUNTER_NOT_EMPTY');
 call(['run','--rm','-T','--no-deps','--pull','never','--entrypoint','sh','api','-c','test -z "$(find /data/catalog-images -mindepth 1 -print -quit)"']);
 const photoArchive=join(directory,'catalog-images.tar.gz');
 if(tar(['-tzf',photoArchive]).split(/\r?\n/).some(n=>n!=='./'&&!/^\.\/[a-f0-9]{64}\.webp$/.test(n))||tar(['-tvzf',photoArchive]).split(/\r?\n/).some(n=>!/^[-d]/.test(n)))fail('UNSAFE_PHOTO_ARCHIVE');
 call(['exec','-T','db','sh','-c','export MYSQL_PWD="$MARIADB_ROOT_PASSWORD"; exec mariadb --user=root --binary-mode'],await readFile(join(directory,'database.sql')));
 call(['run','--rm','-T','--no-deps','--pull','never','--entrypoint','tar','api','--no-same-owner','--no-same-permissions','-C','/data/catalog-images','-xzf','-'],await readFile(photoArchive));
 const accounts=JSON.parse(await readFile(join(recovery,'accounts.private.json'))),technical=accounts.filter(a=>a.user!=='healthcheck');
 const rootAccount=technical.find(a=>a.user==='root'&&a.host==='localhost');
 if(!rootAccount||rootAccount.grants.filter(g=>g.startsWith('GRANT PROXY ')).join('\n')!=='GRANT PROXY ON ``@`%` TO `root`@`localhost` WITH GRANT OPTION')fail('UNEXPECTED_BACKUP_PROXY_POLICY');
 // REVOKE ALL does not remove PROXY. The empty-host bootstrap row is distinct
 // from the backed-up '%' row although SHOW GRANTS renders both identically.
 const bootstrapProxy=query("SELECT COUNT(*) FROM mysql.proxies_priv WHERE Host='localhost' AND User='root' AND Proxied_host='' AND Proxied_user='' AND With_grant=1");
 if(bootstrapProxy!=='1')fail('UNEXPECTED_BOOTSTRAP_PROXY_ROW');
 query("DELETE FROM mysql.proxies_priv WHERE Host='localhost' AND User='root' AND Proxied_host='' AND Proxied_user='' AND With_grant=1; FLUSH PRIVILEGES;");
 if(query("SELECT COUNT(*) FROM mysql.proxies_priv WHERE Host='localhost' AND User='root' AND Proxied_host='' AND Proxied_user=''")!=='0')fail('BOOTSTRAP_PROXY_NOT_REMOVED');
 for(const a of technical){
  const principal=quote(a.user)+'@'+quote(a.host),exists=query(`SELECT COUNT(*) FROM mysql.user WHERE User=${quote(a.user)} AND Host=${quote(a.host)}`)==='1';
  const reset=exists?'REVOKE ALL PRIVILEGES, GRANT OPTION FROM '+principal+';\n'+a.create.replace(/^CREATE USER /,'ALTER USER ')+';\n':a.create+';\n';
  query(reset+a.grants.map(g=>g+';').join('\n'));
  if(JSON.stringify(normalized(query('SHOW GRANTS FOR '+principal).split('\n')))!==JSON.stringify(normalized(a.grants)))fail('TECHNICAL_ACCOUNT_GRANTS_DIFFER');
  const raw=query('SHOW CREATE USER '+principal),actual=raw.startsWith('CREATE USER ')?raw:raw.split('\t').slice(1).join('\t');if(actual!==a.create)fail('TECHNICAL_ACCOUNT_DEFINITION_DIFFERS');
 }
 for(const [t,e]of Object.entries(snapshot.tables))if(Number(query('SELECT COUNT(*) FROM vivero.'+identifier(t)))!==e.rows||query('CHECKSUM TABLE vivero.'+identifier(t)+' EXTENDED').split('\t').at(-1)!==e.checksum)fail('RESTORED_TABLE_DIFFERS');
 for(const [name,sql]of Object.entries(snapshot.object_queries))if(query(sql)!==snapshot.objects[name])fail('RESTORED_OBJECTS_DIFFER');
 if(query('SELECT VERSION(),@@sql_mode,@@time_zone,@@character_set_server,@@collation_server,@@event_scheduler')!==snapshot.server)fail('SERVER_CONFIGURATION_DIFFERS');
 if(JSON.stringify(query('SELECT version FROM vivero.schema_migrations ORDER BY version').split('\n'))!==JSON.stringify(snapshot.migrations))fail('RESTORED_MIGRATIONS_DIFFER');
 const foreignKeys=query("SELECT CONSTRAINT_NAME,TABLE_NAME,COLUMN_NAME,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA='vivero' AND REFERENCED_TABLE_NAME IS NOT NULL ORDER BY TABLE_NAME,CONSTRAINT_NAME,ORDINAL_POSITION").split('\n');
 const grouped=new Map();for(const line of foreignKeys){const [constraint,table,col,parent,parentCol]=line.split('\t'),key=table+'.'+constraint;if(!grouped.has(key))grouped.set(key,{table,parent,cols:[]});grouped.get(key).cols.push([col,parentCol])}
 for(const fk of grouped.values()){
  const joinCondition=fk.cols.map(([c,p])=>'c.'+identifier(c)+'=p.'+identifier(p)).join(' AND '),notNull=fk.cols.map(([c])=>'c.'+identifier(c)+' IS NOT NULL').join(' AND ');
  if(query('SELECT COUNT(*) FROM vivero.'+identifier(fk.table)+' c LEFT JOIN vivero.'+identifier(fk.parent)+' p ON '+joinCondition+' WHERE '+notNull+' AND p.'+identifier(fk.cols[0][1])+' IS NULL')!=='0')fail('ORPHANED_FOREIGN_KEY');
 }
 const checks=query('CHECK TABLE '+Object.keys(snapshot.tables).map(t=>'vivero.'+identifier(t)).join(','));if(checks.split('\n').some(l=>!l.endsWith('\tstatus\tOK')))fail('TABLE_INTEGRITY_CHECK_FAILED');
 const photos=join(work,'photo-reference');await mkdir(photos);tar(['-xzf',photoArchive,'-C',photos]);
 const expectedPhotos={};for(const n of await readdir(photos))expectedPhotos[n]=await sha(join(photos,n));
 const found={};for(const line of call(['run','--rm','-T','--no-deps','--pull','never','--entrypoint','sh','api','-c','find /data/catalog-images -type f -exec sha256sum {} +']).split('\n').filter(Boolean)){const [h,p]=line.split(/\s+/);found[basename(p)]=h}
 if(JSON.stringify(Object.entries(found).sort())!==JSON.stringify(Object.entries(expectedPhotos).sort()))fail('PHOTOGRAPHS_DIFFER');
 // Test the restored application's DB login without running its API or writes.
 const appProbe="import mysql from 'mysql2/promise';const c=await mysql.createConnection({host:process.env.DB_HOST,user:process.env.DB_USER,password:process.env.DB_PASSWORD,database:process.env.DB_NAME});await c.query('SELECT 1');await c.query('SELECT id FROM products LIMIT 1');await c.query('SELECT id FROM sales LIMIT 1');await c.end();";
 call(['run','--rm','-T','--no-deps','--pull','never','--entrypoint','node','api','--input-type=module','-e',appProbe]);
 if(docker(['inspect',container,'--format','{{.State.Health.Status}}'])!=='healthy')fail('RESTORED_HEALTHCHECK_FAILED');
 const result={status:'PASS',backup_restore_verified:true,project,schema:snapshot.migrations.at(-1),tables_verified:Object.keys(snapshot.tables).length,records_verified:Object.values(snapshot.tables).reduce((n,t)=>n+t.rows,0),foreign_keys_verified:grouped.size,technical_accounts_verified:technical.length,application_users_roles_permissions_equal:true,application_database_login_verified:true,objects_definers_grants_equal:true,table_integrity_checks_ok:true,photographs_verified:Object.keys(found).length,bootstrap_proxy_reconciled:true,strict_grants_comparison:true,authenticated_decryption:true,encrypted_sha256:opts['--encrypted-sha256'],archive_sha256:opts['--archive-sha256'],sources_and_images_recovered_from_encrypted_backup:true,no_previous_temp_files_required:true,engine:query('SELECT VERSION()'),production_access:false,migration_033_executed:false,healthcheck_users:'Regenerated by isolated bootstrap; original definitions preserved in encrypted supplement'};
 await writeFile(join(work,'restore-verification.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result));
}catch(e){console.error(/^[A-Z_]+$/.test(e?.message??'')?e.message:'RECOVERY_FAILED_NO_PRIVATE_OUTPUT');process.exitCode=1}
finally{if(container){try{docker(['stop',container])}catch{console.error('ISOLATED_STOP_FAILED');process.exitCode=1}}}
