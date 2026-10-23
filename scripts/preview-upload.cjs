// Local-only preview fixture. Never used by the Android app.
const http=require('node:http'),fs=require('node:fs'),path=require('node:path');
let files=[{id:'demo',name:'晨间风景.jpg',size:2450000,type:'image'}];
http.createServer((req,res)=>{const u=new URL(req.url,'http://localhost');res.setHeader('Content-Type','application/json; charset=utf-8');
if(u.pathname==='/'){res.setHeader('Content-Type','text/html; charset=utf-8');return res.end(fs.readFileSync(path.join(__dirname,'../app/src/main/assets/upload.html')))}
if(req.headers['x-requested-with']!=='TVCarousel'){res.statusCode=403;return res.end('{"error":"missing marker"}')}
if(u.pathname==='/api/files')return res.end(JSON.stringify({files,free:8.6*1073741824,status:'扫描完成：'+files.length+' 项'}));
if(u.pathname==='/api/refresh')return res.end('{"ok":true}');
if(u.pathname==='/api/file'){files=files.filter(f=>f.id!==u.searchParams.get('id'));return res.end('{"ok":true}')}
if(u.pathname==='/api/upload'){let size=0;req.on('data',chunk=>size+=chunk.length);return req.on('end',()=>{files.push({id:String(Date.now()),name:u.searchParams.get('name'),size,type:'image'});res.end('{"ok":true}')})}
res.statusCode=404;res.end('{}')}).listen(Number(process.env.PORT||8765),'127.0.0.1',()=>console.log('Preview fixture: http://127.0.0.1:8765'));

