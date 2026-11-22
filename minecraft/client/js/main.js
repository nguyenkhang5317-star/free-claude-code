import * as THREE from 'three';
import {World, CHUNK, HEIGHT, SEA, ID2NAME, NAME2ID} from './world.js';
import {makeAtlas} from './textures.js';
import {Mob, spawnInitial} from './mobs.js';
import {play, initAudio, setMuted} from './audio.js';

// --- load registries ---
const [blocksJ, itemsJ, mobsJ] = await Promise.all([
  fetch('/data/blocks.json').then(r=>r.json()),
  fetch('/data/items.json').then(r=>r.json()),
  fetch('/data/mobs.json').then(r=>r.json()),
]);
const BLOCKS={}; blocksJ.blocks.forEach(b=>BLOCKS[b.id]=b);
const ITEMS={}; itemsJ.items.forEach(i=>ITEMS[i.id]=i);
const MOBS={}; mobsJ.mobs.forEach(m=>MOBS[m.id.replace('minecraft:','')]=m);

// --- renderer/scene ---
const canvas=document.getElementById('game');
const renderer=new THREE.WebGLRenderer({canvas, antialias:true});
renderer.setSize(innerWidth,innerHeight); renderer.setPixelRatio(Math.min(devicePixelRatio,2));
const scene=new THREE.Scene(); scene.background=new THREE.Color(0x87ceeb); scene.fog=new THREE.Fog(0x87ceeb,40,120);
const camera=new THREE.PerspectiveCamera(75,innerWidth/innerHeight,0.1,500);
const sun=new THREE.DirectionalLight(0xffffff,1.2); sun.position.set(50,100,50); scene.add(sun);
scene.add(new THREE.AmbientLight(0xffffff,0.6));
const atlas=makeAtlas();

// --- world (launcher passes ?seed=&dim=) ---
const _q=new URLSearchParams(location.search);
let seed=_q.get('seed')||document.getElementById('seedInput').value||'12345';
let dim=_q.get('dim')||document.getElementById('worldType').value;
document.getElementById('seedInput').value=seed; document.getElementById('worldType').value=dim;
let world=new World(seed,dim);
document.getElementById('seedLabel').textContent='Seed: '+seed+' ('+dim+')';

// --- player state ---
const player={pos:new THREE.Vector3(8,world.heightAt(8,8)+2,8), vel:new THREE.Vector3(), yaw:0, pitch:0, onGround:false, hp:20, hunger:20, sat:5, xp:0, dead:false, fly:false};
const keys={}; let locked=false, muted=false;
let inventory=new Array(36).fill(null); // {id,count}
let hotbarSel=0; let craftGrid=new Array(9).fill(null);
let timeOfDay=0.3, day=1, tick=0; let mobs=[], breakProg=0, breakTarget=null;

// chunk meshing
const mat=new THREE.MeshLambertMaterial({map:atlas.tex, transparent:true, alphaTest:0.1});
const waterMat=new THREE.MeshLambertMaterial({color:0x3f6fd8, transparent:true, opacity:0.7});
function tileUV(name){ const key=name.replace('minecraft:',''); return atlas.uv[key]||atlas.uv['stone']||[0,0,1,1]; }
function buildChunkMesh(cx,cz){
  const c=world.getChunk(cx,cz);
  if(c.mesh){scene.remove(c.mesh); c.mesh.geometry.dispose(); c.mesh=null;}
  const pos=[], norm=[], uvA=[], idx=[], col=[];
  const faces=[
    {d:[1,0,0], c:[[1,0,0],[1,1,0],[1,1,1],[1,0,1]], n:[1,0,0]},
    {d:[-1,0,0], c:[[0,0,1],[0,1,1],[0,1,0],[0,0,0]], n:[-1,0,0]},
    {d:[0,1,0], c:[[0,1,1],[1,1,1],[1,1,0],[0,1,0]], n:[0,1,0]},
    {d:[0,-1,0], c:[[0,0,0],[1,0,0],[1,0,1],[0,0,1]], n:[0,-1,0]},
    {d:[0,0,1], c:[[0,0,1],[1,0,1],[1,1,1],[0,1,1]], n:[0,0,1]},
    {d:[0,0,-1], c:[[1,0,0],[0,0,0],[0,1,0],[1,1,0]], n:[0,0,-1]},
  ];
  const baseX=cx*CHUNK, baseZ=cz*CHUNK;
  for(let y=0;y<HEIGHT;y++)for(let lz=0;lz<CHUNK;lz++)for(let lx=0;lx<CHUNK;lx++){
    const id=c.data[(y*CHUNK+lz)*CHUNK+lx]; if(!id) continue;
    const name=ID2NAME[id]||'minecraft:stone';
    const isWater=name==='minecraft:water';
    const wx=baseX+lx, wz=baseZ+lz;
    for(const f of faces){
      const nx=wx+f.d[0], ny=y+f.d[1], nz=wz+f.d[2];
      const nid=world.getBlock(nx,ny,nz);
      if(nid!==0 && nid!==14) continue; // hidden
      if(isWater && nid===14) continue;
      const [u0,v0,du,dv]=tileUV(name);
      const vi=pos.length/3;
      f.c.forEach((cc,i)=>{
        pos.push(wx+cc[0], y+cc[1], wz+cc[2]); norm.push(...f.n);
        const uu=[u0,u0+du,u0+du,u0][i], vv=[v0,v0,v0+dv,v0+dv][i];
        uvA.push(uu,vv);
        const shade=f.n[1]>0?1:f.n[1]<0?0.6:f.n[0]!==0?0.8:0.7;
        col.push(shade,shade,shade);
      });
      idx.push(vi,vi+1,vi+2, vi,vi+2,vi+3);
    }
  }
  if(!idx.length) return;
  const g=new THREE.BufferGeometry();
  g.setAttribute('position',new THREE.Float32BufferAttribute(pos,3));
  g.setAttribute('normal',new THREE.Float32BufferAttribute(norm,3));
  g.setAttribute('uv',new THREE.Float32BufferAttribute(uvA,2));
  g.setAttribute('color',new THREE.Float32BufferAttribute(col,3));
  g.setIndex(idx);
  const m=new THREE.Mesh(g,mat); m.matrixAutoUpdate=false; scene.add(m); c.mesh=m; c.dirty=false;
}
function ensureChunks(){
  const pcx=Math.floor(player.pos.x/CHUNK), pcz=Math.floor(player.pos.z/CHUNK), R=4;
  for(let dx=-R;dx<=R;dx++)for(let dz=-R;dz<=R;dz++){
    const cx=pcx+dx, cz=pcz+dz; const c=world.getChunk(cx,cz);
    if(c.dirty) buildChunkMesh(cx,cz);
  }
  // cleanup far
  for(const [k,c] of world.chunks){ const [cx,cz]=k.split(',').map(Number); if(Math.abs(cx-pcx)>R+2||Math.abs(cz-pcz)>R+2){ if(c.mesh){scene.remove(c.mesh);c.mesh.geometry.dispose();c.mesh=null;} world.chunks.delete(k); } }
}

// --- input ---
addEventListener('keydown',e=>{
  keys[e.code]=true;
  if(e.code==='KeyE'){ toggleInv(); }
  if(e.code==='KeyF'){ eat(); }
  if(e.code==='KeyM'){ muted=!muted; setMuted(muted); msg(muted?'Muted':'Sound on'); }
  if(e.code==='Slash'){ document.getElementById('chat').classList.remove('hidden'); document.getElementById('chatInput').focus(); }
  if(e.code.startsWith('Digit')){ hotbarSel=+e.code.slice(5)-1; renderHotbar(); }
  if(e.code==='Escape'){ document.getElementById('chat').classList.add('hidden'); document.getElementById('inventory').classList.add('hidden'); }
});
addEventListener('keyup',e=>keys[e.code]=false);
document.getElementById('chatInput').addEventListener('keydown',e=>{ if(e.key==='Enter'){ runCmd(e.target.value); e.target.value=''; e.target.blur(); document.getElementById('chat').classList.add('hidden'); }});
canvas.addEventListener('click',()=>{ if(!locked){ canvas.requestPointerLock(); initAudio(); }});
document.addEventListener('pointerlockchange',()=>{ locked=document.pointerLockElement===canvas; document.getElementById('menu').style.display=locked?'none':'flex'; });
document.addEventListener('mousemove',e=>{ if(!locked)return; player.yaw-=e.movementX*0.0025; player.pitch-=e.movementY*0.0025; player.pitch=Math.max(-1.5,Math.min(1.5,player.pitch)); });
let mining=false, placing=false;
canvas.addEventListener('mousedown',e=>{ if(!locked)return; if(e.button===0)mining=true; if(e.button===2)placing=true; });
addEventListener('mouseup',e=>{ if(e.button===0)mining=false; if(e.button===2)placing=false; });
canvas.addEventListener('contextmenu',e=>e.preventDefault());

// --- physics (fixed tick 20 TPS, gravity 0.08, damping 0.98) ---
function collide(pos){
  const r=0.3, h=1.8;
  for(let y=0;y<2;y++)for(let x=-1;x<=1;x++)for(let z=-1;z<=1;z++){
    const bx=Math.floor(pos.x+x*0.3), by=Math.floor(pos.y+y), bz=Math.floor(pos.z+z*0.3);
    if(world.isSolid(bx,by,bz)) return true;
  }
  return false;
}
function tickPlayer(){
  if(player.dead) return;
  const speed=keys['ShiftLeft']?0.12:0.22;
  const f=new THREE.Vector3(-Math.sin(player.yaw),0,-Math.cos(player.yaw));
  const r=new THREE.Vector3(-f.z,0,f.x);
  let mv=new THREE.Vector3();
  if(keys['KeyW'])mv.add(f); if(keys['KeyS'])mv.sub(f); if(keys['KeyD'])mv.add(r); if(keys['KeyA'])mv.sub(r);
  if(mv.lengthSq()>0)mv.normalize().multiplyScalar(speed);
  player.vel.x=mv.x; player.vel.z=mv.z;
  if(keys['Space']&&player.onGround){player.vel.y=0.42; player.onGround=false;}
  player.vel.y-=0.08; player.vel.y*=0.98;
  if(player.fly){player.vel.y=0; if(keys['Space'])player.vel.y=0.2; if(keys['ShiftLeft'])player.vel.y=-0.2;}
  const oldY=player.pos.y;
  player.pos.addScaledVector(player.vel,1);
  // simple AABB collision per axis
  if(collide(player.pos)){ player.pos.y=oldY; if(player.vel.y<0){player.onGround=true; if(player.vel.y<-0.5)damagePlayer(Math.floor(-player.vel.y*4),'fall');} player.vel.y=0; }
  else player.onGround=false;
  // world border / void
  if(player.pos.y<0){ damagePlayer(4,'void'); player.pos.y=world.heightAt(Math.floor(player.pos.x),Math.floor(player.pos.z))+2; }
  camera.position.copy(player.pos).add(new THREE.Vector3(0,1.62,0));
  camera.rotation.set(player.pitch,player.yaw,0,'YXZ');
  // hunger/health
  if(tick%100===0){ if(player.hunger>0&&Math.random()<0.3)player.hunger--; if(player.hunger<=0)damagePlayer(1,'starve'); if(player.hunger>14&&player.hp<20){player.hp=Math.min(20,player.hp+1); player.hunger--;} }
  // mining
  const dir=new THREE.Vector3(); camera.getWorldDirection(dir);
  const hit=world.raycast(camera.position.x,camera.position.y,camera.position.z,dir.x,dir.y,dir.z,6);
  if(mining&&hit){
    const name=ID2NAME[hit.id]; const def=BLOCKS[name]||{hardness:1};
    breakProg+=1/(def.hardness*20||1);
    document.getElementById('breakProgress').style.display='block';
    document.getElementById('breakFill').style.width=(breakProg*100)+'%';
    if(breakProg>=1){ world.setBlock(hit.x,hit.y,hit.z,0); addItem(name,1); play('block.stone.break'); breakProg=0; }
  } else { breakProg=0; document.getElementById('breakProgress').style.display='none'; }
  if(placing&&hit){
    const sel=inventory[hotbarSel]; if(sel){ const id=NAME2ID[sel.id]||1; const px=hit.x+hit.face[0],py=hit.y+hit.face[1],pz=hit.z+hit.face[2]; if(world.getBlock(px,py,pz)===0){ world.setBlock(px,py,pz,id); sel.count--; if(sel.count<=0)inventory[hotbarSel]=null; play('block.stone.place'); renderHotbar(); } }
  }
  // mobs
  mobs.forEach(m=>m.update(0.05,player.pos,world));
  mobs=mobs.filter(m=>!m.dead||m.mesh.parent);
  mobs.forEach(m=>{if(m.dead&&m.mesh.parent){scene.remove(m.mesh);}});
  mobs=mobs.filter(m=>!m.dead);
  // day/night
  timeOfDay=(timeOfDay+0.0004)%1; if(timeOfDay<0.01)day++;
  const ang=timeOfDay*Math.PI*2; sun.position.set(Math.cos(ang)*80,Math.sin(ang)*80+20,30);
  const dayF=Math.max(0,Math.sin(ang)); sun.intensity=0.3+dayF*1.0;
  scene.background.setHSL(0.58,0.6,0.25+dayF*0.45);
  document.getElementById('timeLabel').textContent=`Day ${day} ${String(Math.floor(timeOfDay*24)).padStart(2,'0')}:00`;
  document.getElementById('pos').textContent=`${Math.floor(player.pos.x)},${Math.floor(player.pos.y)},${Math.floor(player.pos.z)}`;
  document.getElementById('hearts').textContent='❤️'.repeat(Math.ceil(player.hp/2))+'🖤'.repeat(10-Math.ceil(player.hp/2));
  document.getElementById('hunger').textContent='🍗'.repeat(Math.ceil(player.hunger/2))+'🖤'.repeat(10-Math.ceil(player.hunger/2));
}
function damagePlayer(n,src){ if(player.dead)return; player.hp-=n; play('entity.player.hurt'); if(player.hp<=0){ player.dead=true; document.getElementById('death').classList.remove('hidden'); document.getElementById('deathMsg').textContent='Killed by '+src; document.exitPointerLock(); } }
window.__damagePlayer=(n,src)=>damagePlayer(n,src);
window.__onMobDeath=(type,pos)=>{ addItem('minecraft:rotten_flesh',1); player.xp+=5; play('entity.experience_orb.pickup'); msg(`+5 XP — ${type} died`); };
document.getElementById('respawnBtn').onclick=()=>{ player.hp=20; player.hunger=20; player.dead=false; player.pos.set(8,world.heightAt(8,8)+2,8); document.getElementById('death').classList.add('hidden'); canvas.requestPointerLock(); };

// --- inventory/crafting ---
function addItem(id,n=1){ for(let i=0;i<36;i++){ if(inventory[i]&&inventory[i].id===id&&inventory[i].count<64){inventory[i].count+=n; renderHotbar(); return;} } for(let i=0;i<36;i++){ if(!inventory[i]){inventory[i]={id,count:n}; renderHotbar(); return;} } msg('Inventory full!'); }
function eat(){ const sel=inventory[hotbarSel]; if(sel&&ITEMS[sel.id]?.food){ player.hunger=Math.min(20,player.hunger+ITEMS[sel.id].food); sel.count--; if(sel.count<=0)inventory[hotbarSel]=null; renderHotbar(); msg('Ate '+sel.id); } else msg('Nothing to eat — select food'); }
function renderHotbar(){
  const hb=document.getElementById('hotbar'); hb.innerHTML='';
  for(let i=0;i<9;i++){ const s=inventory[i]; const d=document.createElement('div'); d.className='slot'+(i===hotbarSel?' sel':''); d.innerHTML=s?`<div class="sw" style="background:hsl(${s.id.length*37%360},60%,50%)"></div><span>${s.id.replace('minecraft:','')}</span><span class="cnt">${s.count}</span>`:`<span>${i+1}</span>`; d.onclick=()=>{hotbarSel=i;renderHotbar()}; hb.appendChild(d); }
  renderInv();
}
function renderInv(){
  const g=document.getElementById('invGrid'); if(!g)return; g.innerHTML='';
  inventory.forEach((s,i)=>{ const d=document.createElement('div'); d.className='islot'; d.textContent=s?s.id.replace('minecraft:','')+'×'+s.count:''; d.onclick=()=>{ if(s){inventory[hotbarSel]=s; inventory[i]=null; renderHotbar();} }; g.appendChild(d); });
  const cg=document.getElementById('craftGrid'); cg.innerHTML='';
  craftGrid.forEach((s,i)=>{ const d=document.createElement('div'); d.className='islot'; d.textContent=s?s.id.replace('minecraft:',''):''; d.onclick=()=>{ craftGrid[i]=null; renderInv(); }; cg.appendChild(d); });
  document.getElementById('craftOut').innerHTML='<button id="craftBtn">Craft</button>';
  document.getElementById('craftBtn').onclick=doCraft;
}
const RECIPES=[
  {in:['minecraft:oak_log'],out:['minecraft:oak_planks',4]},
  {in:['minecraft:oak_planks','minecraft:oak_planks'],out:['minecraft:stick',4]},
  {in:['minecraft:cobblestone','minecraft:cobblestone','minecraft:cobblestone','minecraft:cobblestone','minecraft:cobblestone','minecraft:cobblestone','minecraft:cobblestone','minecraft:cobblestone','minecraft:cobblestone'],out:['minecraft:furnace',1]},
  {in:['minecraft:oak_planks','minecraft:oak_planks','minecraft:oak_planks','minecraft:oak_planks'],out:['minecraft:crafting_table',1]},
  {in:['minecraft:oak_planks','minecraft:oak_planks','minecraft:oak_planks','minecraft:oak_planks','minecraft:oak_planks','minecraft:oak_planks'],out:['minecraft:oak_log',1]},
];
function doCraft(){
  const have=craftGrid.filter(Boolean).map(s=>s.id).sort().join(',');
  for(const r of RECIPES){ if(r.in.slice().sort().join(',')===have){ addItem(r.out[0],r.out[1]); craftGrid.fill(null); play('ui.button.click'); msg('Crafted '+r.out[0]); renderInv(); return; } }
  msg('No recipe');
}
function toggleInv(){ const el=document.getElementById('inventory'); el.classList.toggle('hidden'); if(!el.classList.contains('hidden')){ document.exitPointerLock(); renderInv(); } }
document.getElementById('closeInv').onclick=toggleInv;
function msg(t){ const m=document.getElementById('msg'); m.textContent=t; setTimeout(()=>m.textContent='',2500); }

// --- commands ---
function runCmd(c){
  c=c.trim(); if(!c.startsWith('/'))return; const [cmd,...rest]=c.slice(1).split(/\s+/);
  if(cmd==='help')msg('/seed /time set day|night /gamemode /give <item> /tp /spawn /weather /kill /seed');
  else if(cmd==='seed'){ seed=rest[0]||seed; world=new World(seed,dim); mobs.forEach(m=>scene.remove(m.mesh)); mobs=spawnInitial(scene,MOBS,world,player.pos); msg('New seed '+seed); }
  else if(cmd==='time'){ timeOfDay=rest[1]==='night'?0.75:0.3; msg('Time set'); }
  else if(cmd==='give'){ addItem(rest[0]||'minecraft:oak_log',+rest[1]||1); }
  else if(cmd==='tp'){ player.pos.set(+rest[0]||0,+rest[1]||40,+rest[2]||0); }
  else if(cmd==='spawn'){ player.pos.set(8,world.heightAt(8,8)+2,8); }
  else if(cmd==='gamemode'){ player.fly=rest[0]==='creative'; msg('Fly: '+player.fly); }
  else if(cmd==='kill'){ damagePlayer(999,'command'); }
  else if(cmd==='weather'){ msg('Weather: clear (rain not implemented)'); }
  else msg('Unknown: '+cmd);
}

// --- boot ---
document.getElementById('playBtn').onclick=()=>{ initAudio(); canvas.requestPointerLock(); };
mobs=spawnInitial(scene,MOBS,world,player.pos);
renderHotbar(); addItem('minecraft:oak_log',16); addItem('minecraft:oak_planks',16); addItem('minecraft:stick',8); addItem('minecraft:torch',8); addItem('minecraft:apple',4);
// starter items defs fallback
ITEMS['minecraft:apple']={food:4};
ensureChunks();

// --- fixed tick 20 TPS decoupled render ---
let last=performance.now(), acc=0, frames=0, fpsT=performance.now();
function loop(now){
  requestAnimationFrame(loop);
  const dt=Math.min(0.25,(now-last)/1000); last=now; acc+=dt;
  const tickDur=0.05;
  let n=0; while(acc>=tickDur&&n<4){ tickPlayer(); tick++; acc-=tickDur; n++; }
  ensureChunks();
  renderer.render(scene,camera);
  frames++; if(now-fpsT>1000){ document.getElementById('fps').textContent=frames+' FPS · '+tick+' ticks'; frames=0; fpsT=now; }
}
requestAnimationFrame(loop);
addEventListener('resize',()=>{camera.aspect=innerWidth/innerHeight;camera.updateProjectionMatrix();renderer.setSize(innerWidth,innerHeight)});
