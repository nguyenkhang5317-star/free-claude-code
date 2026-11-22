// Mobs - models as boxes, AI wander/follow/attack, drops (§8-10)
import * as THREE from 'three';
import {play} from './audio.js';
const COLORS={cow:0x8b7355,pig:0xf0a0b0,sheep:0xeeeeee,chicken:0xffffff,zombie:0x3a7d44,skeleton:0xcccccc,spider:0x333333,creeper:0x4caf50,slime:0x7ec850,witch:0x6a3d9a,bee:0xffcc33,cat:0x888888,wolf:0x999999,horse:0x8b5a2b,rabbit:0xcccccc,mooshroom:0xb03060,ocelot:0xd4a017,parrot:0xff4444,fox:0xcc7722,bat:0x222222,squid:0x4444aa,turtle:0x2e8b57,villager:0x9a7b4f,wandering_trader:0x6b4f2a,iron_golem:0xdddddd,snow_golem:0xffffff,blaze:0xff8800,ghast:0xeeeeee,piglin:0xcc8866,hoglin:0x8b4513,zoglin:0x556b2f,magma_cube:0xff4400,ender_dragon:0x222222,wither:0x111111,phantom:0x333355,drowned:0x3a6ea5,husk:0xb89a6a,stray:0xaaaaaa,evoker:0x4a2a6a,vindicator:0x5a4a3a,pillager:0x7a6a5a,ravager:0x6a4a3a,vex:0x8888cc,allay:0x88ccff,axolotl:0xff99cc,goat:0xdddddd,frog:0x66cc66,tadpole:0x88aa66,warden:0x1a3a4a,allay2:0x88ccff};
export class Mob{
  constructor(type,pos,registry){
    this.type=type; this.def=registry?.[type]||{health:10,speed:0.2,width:0.9,height:1.4};
    this.pos=new THREE.Vector3(...pos); this.vel=new THREE.Vector3();
    this.hp=this.def.health*2; this.maxHp=this.hp; // half-units
    this.yaw=Math.random()*Math.PI*2; this.state='wander'; this.timer=0; this.hurtT=0; this.dead=false;
    this.mesh=this.buildMesh(); this.mesh.position.copy(this.pos);
  }
  buildMesh(){
    const g=new THREE.Group();
    const c=COLORS[this.type]||0x888888;
    const mat=new THREE.MeshLambertMaterial({color:c});
    const w=this.def.width||0.9, h=this.def.height||1.4;
    const body=new THREE.Mesh(new THREE.BoxGeometry(w,h*0.6,w*1.4),mat); body.position.y=h*0.55; g.add(body);
    const head=new THREE.Mesh(new THREE.BoxGeometry(w*0.6,w*0.6,w*0.6),mat); head.position.set(0,h*0.85,w*0.8); g.add(head);
    // legs
    const legG=new THREE.BoxGeometry(0.18,h*0.4,0.18);
    [[-w*0.3,-w*0.5],[w*0.3,-w*0.5],[-w*0.3,w*0.5],[w*0.3,w*0.5]].forEach(([x,z])=>{const l=new THREE.Mesh(legG,mat);l.position.set(x,h*0.2,z);g.add(l)});
    // eyes
    const eyeM=new THREE.MeshBasicMaterial({color:0x000000});
    [-0.12,0.12].forEach(x=>{const e=new THREE.Mesh(new THREE.BoxGeometry(0.06,0.06,0.02),eyeM);e.position.set(x,h*0.88,w*0.8+0.31);g.add(e)});
    g.userData={body,head};
    return g;
  }
  update(dt,playerPos,world){
    if(this.dead) return;
    this.timer-=dt; this.hurtT-=dt;
    const toPlayer=playerPos.clone().sub(this.pos); const dist=toPlayer.length();
    const hostile=['zombie','skeleton','spider','creeper','slime','witch','blaze','ghast','piglin','hoglin','zoglin','magma_cube','phantom','drowned','husk','stray','evoker','vindicator','pillager','ravager','vex','warden','ender_dragon','wither'].includes(this.type);
    let move=new THREE.Vector3();
    if(hostile && dist<16 && dist>1.2){ move.copy(toPlayer).normalize(); this.yaw=Math.atan2(toPlayer.x,toPlayer.z); if(dist<2 && this.timer<=0){ this.attack(playerPos); this.timer=1; } }
    else if(this.timer<=0){ this.yaw+= (Math.random()-0.5)*1.5; this.timer=2+Math.random()*3; }
    if(this.state==='wander'||hostile){ move.set(Math.sin(this.yaw),0,Math.cos(this.yaw)).multiplyScalar(0.4); }
    // gravity + ground
    this.vel.y-=0.08; // per tick approx
    this.pos.addScaledVector(move,(this.def.speed||0.2)*dt*20*0.05);
    this.pos.y+=this.vel.y*dt*20*0.05;
    const groundY=world.heightAt(Math.floor(this.pos.x),Math.floor(this.pos.z))+1;
    if(this.pos.y<groundY){this.pos.y=groundY;this.vel.y=0}
    this.mesh.position.copy(this.pos); this.mesh.rotation.y=this.yaw;
    // walk anim
    const t=performance.now()*0.01;
    this.mesh.userData.body.position.y=(this.def.height||1.4)*0.55+Math.abs(Math.sin(t))*0.05;
    if(this.hurtT>0) this.mesh.traverse(o=>{if(o.material&&o.material.color)o.material.color.setHex(0xff4444)});
    else this.mesh.traverse(o=>{if(o.material&&o.material.color&&o.material.color.getHex()===0xff4444)o.material.color.setHex(COLORS[this.type]||0x888888)});
  }
  attack(playerPos){ play('entity.zombie.hurt'); if(window.__damagePlayer) window.__damagePlayer(4,this.type); }
  damage(amount,src){ this.hp-=amount; this.hurtT=0.3; play(`entity.${this.type}.hurt`); if(this.hp<=0){this.dead=true; this.drops();} }
  drops(){ if(window.__onMobDeath) window.__onMobDeath(this.type,this.pos); }
}
export function spawnInitial(scene,registry,world,center){
  const mobs=[]; const types=['cow','pig','sheep','chicken','zombie','skeleton','spider','creeper','bee','cat','wolf','horse','rabbit','villager','iron_golem'];
  for(let i=0;i<18;i++){ const t=types[i%types.length]; const x=center.x+(Math.random()-0.5)*40, z=center.z+(Math.random()-0.5)*40; const y=world.heightAt(Math.floor(x),Math.floor(z))+1; const m=new Mob(t,[x,y,z],registry); scene.add(m.mesh); mobs.push(m); }
  return mobs;
}
