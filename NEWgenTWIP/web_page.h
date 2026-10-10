// ============================================================
// File: web_page.h
// ============================================================
#pragma once

const char PAGE[] = R"HTML(
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>TWIP Debug</title>
<style>
:root{color-scheme:dark;font-family:system-ui,sans-serif;background:#0b1220;color:#e5edf8}
*{box-sizing:border-box}
body{max-width:1300px;margin:auto;padding:16px}
header,.row{display:flex;gap:10px;align-items:center;flex-wrap:wrap}
header{justify-content:space-between;margin-bottom:14px}
h1{font-size:1.4rem}h2{font-size:1rem;margin-top:0}
.layout{display:grid;grid-template-columns:310px 1fr;gap:14px}
.stack{display:grid;gap:14px;align-content:start}
.panel,.card{background:#142034;border:1px solid #30435f;border-radius:10px;padding:12px}
.badge{padding:7px 10px;border-radius:7px;background:#374151}
.good{background:#14532d}.bad{background:#7f1d1d}
.note,.label{color:#a9bad1;font-size:.82rem;line-height:1.45}
button,input{padding:9px;border:1px solid #4b6283;border-radius:6px;background:#21334d;color:white}
button{cursor:pointer}button:disabled{opacity:.4;cursor:not-allowed}
.danger{background:#7f1d1d}
input[type=number]{width:100%;min-width:0}
input[type=range]{width:100%}
table{width:100%;border-collapse:collapse}
td,th{padding:4px;text-align:left;font-size:.85rem}
#pad{width:220px;height:220px;margin:16px auto;border-radius:50%;border:2px solid #476381;
position:relative;touch-action:none;user-select:none;background:#0c1728}
#knob{position:absolute;left:50%;top:50%;width:56px;height:56px;border-radius:50%;
background:#38bdf8;pointer-events:none;transform:translate(-50%,-50%)}
#cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(140px,1fr));gap:8px;margin-top:12px}
.value{font-size:1.1rem;margin-top:6px;font-variant-numeric:tabular-nums}
.plots{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}
canvas{display:block;width:100%;height:190px}
.legend{font-size:.78rem;margin-top:5px}
#message{min-height:3em}
@media(max-width:850px){.layout{grid-template-columns:1fr}}
@media(max-width:520px){.plots{grid-template-columns:1fr}}
</style>
</head>
<body>
<header>
<h1>TWIP MPU6500 Debug</h1>
<div class="row">
<span id="connection" class="badge">Connecting</span>
<span id="state" class="badge">Unknown</span>
</div>
</header>

<div class="layout">
<aside class="stack">
<section class="panel">
<h2>Operating state</h2>
<div id="fault" class="note">Waiting for data.</div>
<p id="ready" class="note"></p>
<div class="row">
<button id="claim" disabled>Take control</button>
<button id="arm" disabled>Arm / rearm</button>
<button id="disarm" class="danger" disabled>Disarm</button>
<button id="neutral" disabled>Neutral</button>
</div>
<p class="note">
Neutral requests zero travel while balancing. Disarm stops the motors:
support the robot first. Neither replaces an independent physical stop.
</p>
</section>

<section class="panel">
<h2>Drive</h2>
<label>Command scale: <strong id="scaleValue">20%</strong></label>
<input id="scale" type="range" min="5" max="100" value="20">
<div id="pad" aria-label="Hold and drag joystick"><div id="knob"></div></div>
<div id="driveText" class="note">V: 0 · S: 0 steps/s</div>
<p class="note">Requires control ownership, ARMED state, fresh telemetry and the
speed loop enabled. Hold to drive; release to request neutral.</p>
</section>

<section class="panel">
<h2>PID tuning (disarmed only)</h2>
<table>
<thead><tr><th>Loop</th><th>Kp</th><th>Ki</th><th>Kd</th></tr></thead>
<tbody>
<tr><td>Angle</td>
<td><input id="g0" type="number" step="any" aria-label="Angle Kp"></td>
<td><input id="g1" type="number" step="any" aria-label="Angle Ki"></td>
<td><input id="g2" type="number" step="any" aria-label="Angle Kd"></td></tr>
<tr><td>Speed</td>
<td><input id="g3" type="number" step="any" aria-label="Speed Kp"></td>
<td><input id="g4" type="number" step="any" aria-label="Speed Ki"></td>
<td><input id="g5" type="number" step="any" aria-label="Speed Kd"></td></tr>
</tbody>
</table>
<p><label><input id="speedLoop" type="checkbox"> Enable outer speed loop</label></p>
<div class="row">
<button id="read" disabled>Read applied</button>
<button id="apply" disabled>Apply</button>
</div>
<p id="message" class="note"></p>

<h2 style="margin-top:14px">Angle offset</h2>
<p class="note">
Shifts what pitch angle counts as upright. Use this to correct for an
off-center IMU or uneven weight, not to mask a wiring or sign problem.
Change in small steps and re-arm to test. Disarmed only, range ±15°.
</p>
<div class="row">
<input id="offset" type="number" step="0.1" min="-15" max="15"
 aria-label="Angle offset degrees" style="flex:1">
<button id="applyOffset" disabled>Apply offset</button>
</div>
<p id="offsetMessage" class="note"></p>
</section>
</aside>

<main class="stack">
<section class="panel">
<div class="row">
<button id="pause">Pause charts</button>
<button id="clear">Clear recording</button>
<button id="csv">Download CSV</button>
</div>
<p class="note">20 Hz snapshots; latest 1,200 samples kept. Charts show 10 s.
CSV includes every telemetry field. Speeds are pulse-derived, not measured wheel motion.</p>
<div id="cards"></div>
</section>
<div id="plots" class="plots"></div>
<p class="note" id="build"></p>
</main>
</div>

<script>
'use strict';
const $=id=>document.getElementById(id);
const finite=v=>typeof v==='number'&&Number.isFinite(v);

const fields=[
 ['pitch','Filtered pitch','°',2],
 ['accelPitch','Accel pitch','°',2],
 ['gyroRate','Corrected gyro X','°/s',2],
 ['temperatureC','IMU temperature','°C',1],
 ['angleTarget','Total angle target','°',2],
 ['speedTarget','Requested speed','steps/s',0],
 ['speedEstimate','Pulse-derived speed','steps/s',0],
 ['motorRaw','Unconstrained output','steps/s',0],
 ['motorApplied','Applied balance rate','steps/s',0],
 ['leftRate','Left requested','steps/s',0],
 ['rightRate','Right requested','steps/s',0],
 ['leftActual','Left actual pulses','steps/s',0],
 ['rightActual','Right actual pulses','steps/s',0],
 ['angleOffset','Angle offset','°',2],
 ['periodUs','Last period','µs',0],
 ['executionUs','Control work','µs',0],
 ['maxPeriodUs','Maximum period','µs',0],
 ['latePeriods','Periods >6 ms','',0],
 ['imuFailures','IMU failures','',0],
 ['noNewSamples','No-new-sample polls','',0],
 ['imuAgeMs','IMU age at send','ms',0],
 ['sampleAgeMs','Snapshot age at send','ms',0],
 ['angleLimited','Angle output limited','',0],
 ['speedLimited','Lean target limited','',0],
 ['motorLeaseExpired','Motor watchdog tripped','',0],
 ['heapBytes','Free heap','bytes',0]
];

for(const [key,label] of fields){
 const card=document.createElement('div');card.className='card';
 const name=document.createElement('div');name.className='label';name.textContent=label;
 const value=document.createElement('div');value.className='value';
 value.id='v-'+key;value.textContent='—';
 card.append(name,value);$('cards').append(card);
}

const colors=['#38bdf8','#fbbf24','#a78bfa'];
const definitions=[
 ['angle','Balance angle · °',['pitch','angleTarget','accelPitch']],
 ['speed','Speed · steps/s',['speedTarget','speedEstimate','motorApplied']],
 ['pid','Angle PID contributions · steps/s',['angleP','angleI','angleD']],
 ['wheels','Wheel pulses · steps/s',['leftRate','leftActual','rightActual']],
 ['accel','Accelerometer · raw counts',['axRaw','ayRaw','azRaw']],
 ['gyro','Gyroscope · raw counts',['gxRaw','gyRaw','gzRaw']]
];

for(const [id,title,keys] of definitions){
 const panel=document.createElement('section');panel.className='panel';
 const heading=document.createElement('h2');heading.textContent=title;
 const canvas=document.createElement('canvas');canvas.id=id;
 const legend=document.createElement('div');legend.className='legend';
 keys.forEach((key,i)=>{
  const item=document.createElement('span');item.style.color=colors[i];
  item.textContent=(i?' / ':'')+key;legend.append(item);
 });
 panel.append(heading,canvas,legend);$('plots').append(panel);
}

let ws,myId=0,owner=0,operator=false,latest=null,lastRx=0;
let maxDrive=1800,maxSteer=1000;
let history=[],paused=false,dirty=true,lastDraw=0,nextId=0;
const pending=new Map();
let pointer=null,joyX=0,joyY=0;

const connected=()=>ws&&ws.readyState===WebSocket.OPEN;

function setBadge(id,text,good){$(id).textContent=text;$(id).className='badge '+(good?'good':'bad');}

function fresh(){
 if(!connected()||!latest)return false;
 const age=performance.now()-lastRx;
 return age<500&&latest.imuReadOk===true&&
  finite(latest.sampleAgeMs)&&latest.sampleAgeMs+age<250&&
  finite(latest.imuAgeMs)&&latest.imuAgeMs+age<250;
}

const canDrive=()=>operator&&fresh()&&latest.state==='ARMED'&&latest.speedLoop;

function send(message){
 if(!connected()||ws.bufferedAmount>2048)return false;
 try{ws.send(JSON.stringify(message));return true;}catch{return false;}
}

function setOwner(id){
 owner=id;
 const was=operator;
 operator=myId!==0&&owner===myId;
 if(was&&!operator)clearJoy();
 updateButtons();
}

function updateButtons(){
 const live=fresh();
 $('claim').disabled=!connected()||owner!==0;
 $('arm').disabled=!operator||!live||latest?.state==='ARMED'||!latest?.uprightReady;
 $('disarm').disabled=!operator;
 $('neutral').disabled=!operator;
 $('read').disabled=!connected();
 $('apply').disabled=!operator||!live||latest?.state==='ARMED';
 $('applyOffset').disabled=!operator||!live||latest?.state==='ARMED';
}

function request(type,extra={}){
 if(!connected())return;
 const id=++nextId;
 const timer=setTimeout(()=>{
  if(!pending.has(id))return;
  pending.delete(id);
  $('message').textContent='No acknowledgment for '+type+'. Status uncertain; check telemetry.';
 },2500);
 pending.set(id,{timer,type});
 if(!send({type,id,...extra})){
  clearTimeout(timer);pending.delete(id);
  $('message').textContent='Request not sent.';
 }else{
  $('message').textContent='Waiting for '+type+' acknowledgment…';
 }
}

function connect(){
 const protocol=location.protocol==='https:'?'wss:':'ws:';

 ws=new WebSocket(protocol+'//'+location.host+'/ws');


 ws.onopen=()=>{

  latest=null;

  setBadge('connection','Connected; registering...',false);

  // Safely ask the server for hello config now that socket is open

  ws.send(JSON.stringify({type: 'hello'}));

 };


 ws.onmessage=event=>{
  let d;try{d=JSON.parse(event.data);}catch{return;}

  if(d.type==='hello'){
   myId=d.you;maxDrive=d.maxDrive;maxSteer=d.maxSteer;
   $('build').textContent='Firmware: '+d.build;
   setOwner(d.owner);
   clearJoy();
   request('getPid');
   return;
  }

  if(d.type==='owner'){setOwner(d.owner);return;}

  if(d.type==='reply'){
   const waiting=pending.get(d.id);
   if(!waiting)return;
   clearTimeout(waiting.timer);pending.delete(d.id);

   const isOffsetRequest=waiting.type==='setOffset';
   const targetMessage=isOffsetRequest?'offsetMessage':'message';
   $(targetMessage).textContent=(d.ok?'':'Rejected: ')+d.message;

   if(d.ok&&(waiting.type==='getPid'||waiting.type==='setPid')&&
      Array.isArray(d.gains)&&d.gains.length===6&&d.gains.every(finite)){
    d.gains.forEach((v,i)=>$('g'+i).value=v);
    $('speedLoop').checked=d.speedLoop;
   }
   if(finite(d.offset))$('offset').value=d.offset;
   return;
  }

  if(d.type!=='telemetry'||!Number.isInteger(d.seq))return;

  if(latest&&d.seq<latest.seq){history=[];neutral();}

  const isNew=!latest||latest.seq!==d.seq;
  latest=d;lastRx=performance.now();

  $('fault').textContent='Fault: '+d.fault;
  $('ready').textContent=d.uprightReady?'Stable upright condition satisfied.':
   'Hold upright and still for one second before arming.';
  setBadge('state',d.state,d.state==='ARMED');

  for(const [key,,unit,places] of fields){
   const value=d[key];
   $('v-'+key).textContent=typeof value==='boolean'?(value?'YES':'NO'):
    finite(value)?`${value.toFixed(places)} ${unit}`.trim():'—';
  }

  if(isNew){
   history.push({...d,browserTime:new Date().toISOString()});
   if(history.length>1200)history.shift();
   dirty=true;
  }

  if(pointer!==null&&!canDrive())neutral();
  updateButtons();
 };

 ws.onclose=()=>{
  clearJoy();myId=0;setOwner(0);
  for(const item of pending.values())clearTimeout(item.timer);
  pending.clear();
  $('message').textContent='Disconnected. Unacknowledged requests have uncertain status.';
  setBadge('connection','Disconnected; stale readings',false);
  setTimeout(connect,1500);
 };

 ws.onerror=()=>ws.close();
}

function renderJoy(){
 $('knob').style.transform=`translate(-50%,-50%) translate(${joyX*78}px,${joyY*78}px)`;
 const scale=Number($('scale').value)/100;
 $('driveText').textContent=`V: ${Math.round(-joyY*maxDrive*scale)} · S: ${Math.round(joyX*maxSteer*scale)} steps/s`;
}

function clearJoy(){
 const old=pointer;
 pointer=null;joyX=joyY=0;renderJoy();
 if(old!==null&&$('pad').hasPointerCapture(old))$('pad').releasePointerCapture(old);
}

function neutral(){
 clearJoy();
 if(operator)send({type:'drive',v:0,s:0});
}

function moveJoy(event){
 if(event.pointerId!==pointer)return;
 const rect=$('pad').getBoundingClientRect();
 let x=(event.clientX-rect.left-rect.width/2)/78;
 let y=(event.clientY-rect.top-rect.height/2)/78;
 const distance=Math.hypot(x,y);
 if(distance>1){x/=distance;y/=distance;}
 joyX=x;joyY=y;renderJoy();
}

function transmitDrive(){
 if(pointer===null)return;
 if(!canDrive()){neutral();return;}
 const scale=Number($('scale').value)/100;
 if(!send({type:'drive',v:Math.round(-joyY*maxDrive*scale),s:Math.round(joyX*maxSteer*scale)}))neutral();
}

$('pad').onpointerdown=event=>{
 if(pointer!==null||!canDrive()||(event.pointerType==='mouse'&&event.button!==0))return;
 event.preventDefault();
 pointer=event.pointerId;
 $('pad').setPointerCapture(pointer);
 moveJoy(event);transmitDrive();
};
$('pad').onpointermove=moveJoy;
for(const name of ['pointerup','pointercancel','lostpointercapture']){
 $('pad').addEventListener(name,event=>{if(event.pointerId===pointer)neutral();});
}

window.addEventListener('blur',neutral);
window.addEventListener('pagehide',neutral);
document.addEventListener('visibilitychange',()=>{if(document.hidden)neutral();});

$('neutral').onclick=neutral;
$('claim').onclick=()=>request('claim');
$('arm').onclick=()=>{
 neutral();
 if(confirm('Arm the robot? Support it and keep wheels clear.'))request('arm');
};
$('disarm').onclick=()=>{neutral();request('disarm');};
$('scale').oninput=()=>{$('scaleValue').textContent=$('scale').value+'%';renderJoy();};
$('read').onclick=()=>request('getPid');
$('apply').onclick=()=>{
 const text=Array.from({length:6},(_,i)=>$('g'+i).value.trim());
 if(text.some(v=>v==='')){$('message').textContent='Enter all six gains.';return;}
 const gains=text.map(Number);
 if(!gains.every(finite)){$('message').textContent='Invalid gain.';return;}
 neutral();
 request('setPid',{gains,speedLoop:$('speedLoop').checked});
};

$('applyOffset').onclick=()=>{
 const text=$('offset').value.trim();
 if(text===''){$('offsetMessage').textContent='Enter an offset.';return;}
 const offset=Number(text);
 if(!finite(offset)){$('offsetMessage').textContent='Invalid offset.';return;}
 if(Math.abs(offset)>15){$('offsetMessage').textContent='Offset must be within ±15°.';return;}
 neutral();
 request('setOffset',{offset});
};

setInterval(()=>{
 const live=fresh();
 const role=operator?'Controller':(owner===0?'No controller':'Read-only');
 const label=!connected()?'Disconnected':role+(live?' · telemetry fresh':' · telemetry stale / IMU error');
 setBadge('connection',label,live);
 if(!canDrive()&&pointer!==null)neutral();
 transmitDrive();
 updateButtons();
},100);

function drawPlot(id,keys){
 const canvas=$(id),w=canvas.clientWidth,h=canvas.clientHeight;
 if(!w||!h)return;
 const ratio=devicePixelRatio||1;
 canvas.width=Math.round(w*ratio);canvas.height=Math.round(h*ratio);
 const ctx=canvas.getContext('2d');
 ctx.setTransform(ratio,0,0,ratio,0,0);ctx.clearRect(0,0,w,h);

 const end=history.length?history[history.length-1].ms:0;
 const age=row=>(end-row.ms)>>>0;
 const rows=history.filter(row=>age(row)<=10000);
 const valid=row=>row.imuReadOk&&finite(row.imuAgeMs)&&row.imuAgeMs<=50&&
  finite(row.sampleAgeMs)&&row.sampleAgeMs<=100;

 let low=Infinity,high=-Infinity;
 for(const row of rows){
  if(!valid(row))continue;
  for(const key of keys)if(finite(row[key])){low=Math.min(low,row[key]);high=Math.max(high,row[key]);print}
 }
 if(!Number.isFinite(low)){low=-1;high=1;}
 const pad=Math.max((high-low)*.12,.5);low-=pad;high+=pad;

 const left=64,right=w-8,top=10,bottom=h-23;
 const x=row=>right-age(row)/10000*(right-left);
 const y=value=>bottom-(value-low)/(high-low)*(bottom-top);

 ctx.font='11px system-ui';
 for(let i=0;i<=4;i++){
  const value=low+(high-low)*i/4,yy=y(value);
  ctx.strokeStyle='#2c405d';ctx.beginPath();ctx.moveTo(left,yy);ctx.lineTo(right,yy);ctx.stroke();
  ctx.fillStyle='#a9bad1';ctx.fillText(value.toFixed(1),2,yy+4);
 }
 ctx.fillText('-10s',left,h-4);ctx.fillText('Latest',right-36,h-4);

 keys.forEach((key,index)=>{
  ctx.strokeStyle=colors[index];ctx.lineWidth=1.5;ctx.beginPath();
  let previous=null;
  for(const row of rows){
   if(!valid(row)||!finite(row[key])){previous=null;continue;}
   const gap=previous?((row.ms-previous.ms)>>>0):0;
   if(!previous||gap>250)ctx.moveTo(x(row),y(row[key]));
   else ctx.lineTo(x(row),y(row[key]));
   previous=row;
  }
  ctx.stroke();
 });
}

function animate(now){
 if(!paused&&dirty&&now-lastDraw>=100){
  definitions.forEach(([id,,keys])=>drawPlot(id,keys));
  lastDraw=now;dirty=false;
 }
 requestAnimationFrame(animate);
}

$('pause').onclick=()=>{paused=!paused;$('pause').textContent=paused?'Resume charts':'Pause charts';dirty=true;};
$('clear').onclick=()=>{history=[];dirty=true;};
$('csv').onclick=()=>{
 if(!history.length){alert('No samples recorded.');return;}
 const columns=[...new Set(history.flatMap(row=>Object.keys(row)))];
 const escape=value=>'"'+String(value??'').replaceAll('"','""')+'"';
 const lines=[columns.map(escape).join(','),...history.map(row=>columns.map(key=>escape(row[key]).join(','))];
 const blob=new Blob([lines.join('\r\n')],{type:'text/csv;charset=utf-8'});
 const url=URL.createObjectURL(blob);
 const link=document.createElement('a');
 link.href=url;link.download='twip-debug-'+Date.now()+'.csv';
 document.body.append(link);link.click();link.remove();
 setTimeout(()=>URL.revokeObjectURL(url),1000);
};

window.addEventListener('resize',()=>dirty=true);
renderJoy();connect();requestAnimationFrame(animate);
</script>
</body>
</html>
)HTML";