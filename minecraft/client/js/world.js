// Deterministic worldgen - seeded value-gradient Perlin (quintic), §4
export function mulberry32(a){return function(){a|=0;a=a+0x6D2B79F5|0;let t=Math.imul(a^a>>>15,1|a);t=t+Math.imul(t^t>>>7,61|t)^t;return((t^t>>>14)>>>0)/4294967296}}
export function hashSeed(str){let h=2166136261;for(let i=0;i<str.length;i++){h^=str.charCodeAt(i);h=Math.imul(h,16777619)}return h>>>0}
class Noise{
  constructor(seed){this.p=new Uint8Array(512);const r=mulberry32(seed);const perm=[...Array(256).keys()];for(let i=255;i>0;i--){const j=Math.floor(r()*(i+1));[perm[i],perm[j]]=[perm[j],perm[i]]}for(let i=0;i<512;i++)this.p[i]=perm[i&255]}
  fade(t){return t*t*t*(t*(t*6-15)+10)}
  grad(h,x,y,z){switch(h&7){case 0:return x+y;case 1:return x-y;case 2:return -x+y;case 3:return -x-y;case 4:return x+z;case 5:return x-z;case 6:return -x+z;default:return -x-z}}
  noise(x,y,z){const X=Math.floor(x)&255,Y=Math.floor(y)&255,Z=Math.floor(z)&255;x-=Math.floor(x);y-=Math.floor(y);z-=Math.floor(z);const u=this.fade(x),v=this.fade(y),w=this.fade(z);const A=this.p[X]+Y,AA=this.p[A]+Z,AB=this.p[A+1]+Z,B=this.p[X+1]+Y,BA=this.p[B]+Z,BB=this.p[B+1]+Z;const l=(a,b,t)=>a+t*(b-a);return l(l(l(this.grad(this.p[AA],x,y,z),this.grad(this.p[BA],x-1,y,z),u),l(this.grad(this.p[AB],x,y-1,z),this.grad(this.p[BB],x-1,y-1,z),u),v),l(l(this.grad(this.p[AA+1],x,y,z-1),this.grad(this.p[BA+1],x-1,y,z-1),u),l(this.grad(this.p[AB+1],x,y-1,z-1),this.grad(this.p[BB+1],x-1,y-1,z-1),u),v),w)}
  fbm(x,y,z,oct=4){let a=0,amp=0.5,f=1;for(let i=0;i<oct;i++){a+=amp*this.noise(x*f,y*f,z*f);amp*=0.5;f*=2}return a}
}
export const CHUNK=16, HEIGHT=64, SEA=28;
export class World{
  constructor(seedStr, dim='overworld'){
    this.seedStr=String(seedStr); this.seed=hashSeed(this.seedStr); this.dim=dim;
    this.noise=new Noise(this.seed); this.noise2=new Noise(this.seed^0x9e3779b9); this.noise3=new Noise(this.seed^0x85ebca6b);
    this.chunks=new Map(); // "cx,cz" -> {data:Uint16Array, mesh, dirty}
    this.blocks=new Map(); // id->def loaded externally
  }
  key(cx,cz){return cx+','+cz}
  heightAt(x,z){
    if(this.dim==='nether') return 20+Math.floor(this.noise.fbm(x*0.01,0,z*0.01,3)*10);
    if(this.dim==='end') return 30+Math.floor(this.noise.fbm(x*0.02,0,z*0.02,2)*4);
    const base=this.noise.fbm(x*0.008,0,z*0.008,4)*22;
    const hills=this.noise2.fbm(x*0.03,0,z*0.03,3)*6;
    const m=Math.abs(this.noise3.fbm(x*0.004,0,z*0.004,2))*18;
    return Math.max(4, Math.floor(SEA-6+base+hills+m));
  }
  biomeAt(x,z){ const t=this.noise2.noise(x*0.002,0,z*0.002); if(t>0.25)return 'desert'; if(t<-0.25)return 'snow'; if(Math.abs(this.noise3.noise(x*0.001,0,z*0.001))<0.08)return 'river'; return 'plains'; }
  genChunk(cx,cz){
    const data=new Uint16Array(CHUNK*HEIGHT*CHUNK);
    const idx=(x,y,z)=> (y*CHUNK+z)*CHUNK+x;
    const rng=mulberry32(this.seed ^ (cx*374761393) ^ (cz*668265263));
    for(let lx=0;lx<CHUNK;lx++)for(let lz=0;lz<CHUNK;lz++){
      const x=cx*CHUNK+lx, z=cz*CHUNK+lz, h=this.heightAt(x,z), bio=this.biomeAt(x,z);
      for(let y=0;y<HEIGHT;y++){
        let id=0;
        if(y===0) id=13; // bedrock
        else if(y<h-3) id=4; // stone
        else if(y<h) id=3; // dirt (id mapping below)
        else if(y===h) id= bio==='desert'?9 : bio==='snow'?22 : 2; // sand/grass
        else if(y<=SEA && this.dim==='overworld') id=14; // water
        // ores in stone
        if(id===4 && y<40){ const r=rng(); if(y<12&&r<0.008)id=17; else if(y<28&&r<0.012)id=18; else if(y<50&&r<0.02)id=19; else if(r<0.015)id=20; }
        // caves: 3D carver
        if(y>2 && y<h-2 && id!==0){ const c=this.noise.fbm(x*0.04,y*0.06,z*0.04,2); if(c>0.62) id=0; }
        data[idx(lx,y,lz)]=id;
      }
      // trees
      if(this.dim==='overworld' && h>SEA && bio==='plains' && rng()<0.012){
        const tx=lx, tz=lz, ty=h+1;
        for(let dy=0;dy<4;dy++) if(ty+dy<HEIGHT) data[idx(tx,ty+dy,tz)]=6;
        for(let dx=-2;dx<=2;dx++)for(let dz=-2;dz<=2;dz++)for(let dy=4;dy<=5;dy++){ const px=tx+dx,pz=tz+dz,py=ty+dy; if(px>=0&&px<16&&pz>=0&&pz<16&&py<HEIGHT&&data[idx(px,py,pz)]===0) data[idx(px,py,pz)]=11; }
      }
    }
    // village (deterministic grid 34/8)
    if(this.dim==='overworld'){
      const vx=Math.round(cx*CHUNK/34)*34, vz=Math.round(cz*CHUNK/34)*34;
      if(Math.abs(cx*CHUNK-vx)<8 && Math.abs(cz*CHUNK-vz)<8 && ((hashSeed(this.seedStr+vx+','+vz)%10)===0)){
        const lx=vx-cx*CHUNK+8, lz=vz-cz*CHUNK+8, h=this.heightAt(vx,vz);
        for(let dx=-3;dx<=3;dx++)for(let dz=-3;dz<=3;dz++){ const px=lx+dx,pz=lz+dz; if(px>=0&&px<16&&pz>=0&&pz<16){ data[idx(px,h+1,pz)]=8; data[idx(px,h+2,pz)]=8; } }
      }
    }
    return {data, dirty:true, mesh:null};
  }
  getChunk(cx,cz){ const k=this.key(cx,cx); const kk=this.key(cx,cz); if(!this.chunks.has(kk)) this.chunks.set(kk,this.genChunk(cx,cz)); return this.chunks.get(kk); }
  getBlock(x,y,z){
    if(y<0||y>=HEIGHT) return 0;
    const cx=Math.floor(x/CHUNK), cz=Math.floor(z/CHUNK);
    const c=this.getChunk(cx,cz); const lx=x-cx*CHUNK, lz=z-cz*CHUNK;
    return c.data[(y*CHUNK+lz)*CHUNK+lx];
  }
  setBlock(x,y,z,id){
    if(y<0||y>=HEIGHT) return;
    const cx=Math.floor(x/CHUNK), cz=Math.floor(z/CHUNK);
    const c=this.getChunk(cx,cz); const lx=x-cx*CHUNK, lz=z-cz*CHUNK;
    c.data[(y*CHUNK+lz)*CHUNK+lx]=id; c.dirty=true;
    // mark neighbors dirty
    if(lx===0)this.getChunk(cx-1,cz).dirty=true; if(lx===15)this.getChunk(cx+1,cz).dirty=true;
    if(lz===0)this.getChunk(cx,cz-1).dirty=true; if(lz===15)this.getChunk(cx,cz+1).dirty=true;
  }
  isSolid(x,y,z){ const id=this.getBlock(x,y,z); return id!==0 && id!==14; }
  raycast(ox,oy,oz,dx,dy,dz,maxDist=6){
    let x=Math.floor(ox),y=Math.floor(oy),z=Math.floor(oz);
    const stepX=dx>0?1:-1, stepY=dy>0?1:-1, stepZ=dz>0?1:-1;
    const tDeltaX=Math.abs(1/(dx||1e-9)), tDeltaY=Math.abs(1/(dy||1e-9)), tDeltaZ=Math.abs(1/(dz||1e-9));
    let tMaxX=dx!==0?((dx>0?(x+1-ox):(ox-x))*tDeltaX):1e9;
    let tMaxY=dy!==0?((dy>0?(y+1-oy):(oy-y))*tDeltaY):1e9;
    let tMaxZ=dz!==0?((dz>0?(z+1-oz):(oz-z))*tDeltaZ):1e9;
    let t=0, face=[0,0,0];
    for(let i=0;i<128;i++){
      if(tMaxX<tMaxY&&tMaxX<tMaxZ){x+=stepX;t=tMaxX;tMaxX+=tDeltaX;face=[-stepX,0,0]}
      else if(tMaxY<tMaxZ){y+=stepY;t=tMaxY;tMaxY+=tDeltaY;face=[0,-stepY,0]}
      else{z+=stepZ;t=tMaxZ;tMaxZ+=tDeltaZ;face=[0,0,-stepZ]}
      if(t>maxDist) break;
      const id=this.getBlock(x,y,z);
      if(id!==0&&id!==14) return {x,y,z,id,face,dist:t};
    }
    return null;
  }
  serialize(){ return {seed:this.seedStr, dim:this.dim, chunks:[...this.chunks.entries()].map(([k,c])=>[k,Array.from(c.data)])}; }
  static deserialize(o){ const w=new World(o.seed,o.dim); o.chunks.forEach(([k,arr])=>{ const [cx,cz]=k.split(',').map(Number); w.chunks.set(k,{data:Uint16Array.from(arr),dirty:true,mesh:null}); }); return w; }
}
// Block ID map (internal numeric -> registry id)
export const ID2NAME={0:'minecraft:air',1:'minecraft:stone',2:'minecraft:grass_block',3:'minecraft:dirt',4:'minecraft:stone',5:'minecraft:cobblestone',6:'minecraft:oak_log',7:'minecraft:oak_planks',8:'minecraft:oak_planks',9:'minecraft:sand',10:'minecraft:glass',11:'minecraft:oak_leaves',12:'minecraft:bricks',13:'minecraft:bedrock',14:'minecraft:water',15:'minecraft:lava',16:'minecraft:gold_ore',17:'minecraft:diamond_ore',18:'minecraft:iron_ore',19:'minecraft:coal_ore',20:'minecraft:redstone_ore',21:'minecraft:lapis_ore',22:'minecraft:snow_block',23:'minecraft:gravel',24:'minecraft:oak_sapling',25:'minecraft:torch',26:'minecraft:crafting_table',27:'minecraft:furnace',28:'minecraft:bookshelf',29:'minecraft:glowstone',30:'minecraft:obsidian',31:'minecraft:soul_sand',32:'minecraft:prismarine',33:'minecraft:sandstone',34:'minecraft:tnt',35:'minecraft:pumpkin',36:'minecraft:melon',37:'minecraft:white_wool',38:'minecraft:white_concrete',39:'minecraft:terracotta',40:'minecraft:stone_bricks',41:'minecraft:mossy_cobblestone',42:'minecraft:nether_bricks',43:'minecraft:netherrack',44:'minecraft:end_stone',45:'minecraft:ice',46:'minecraft:sponge',47:'minecraft:hay_block',48:'minecraft:slime_block',49:'minecraft:honey_block',50:'minecraft:lantern',51:'minecraft:campfire',52:'minecraft:composter',53:'minecraft:barrel',54:'minecraft:smoker',55:'minecraft:blast_furnace',56:'minecraft:grindstone',57:'minecraft:stonecutter',58:'minecraft:cartography_table',59:'minecraft:fletching_table',60:'minecraft:smithing_table',61:'minecraft:loom',62:'minecraft:bell',63:'minecraft:beacon',64:'minecraft:conduit',65:'minecraft:respawn_anchor',66:'minecraft:crying_obsidian',67:'minecraft:basalt',68:'minecraft:blackstone',69:'minecraft:shroomlight',70:'minecraft:warped_planks',71:'minecraft:crimson_planks',72:'minecraft:ancient_debris',73:'minecraft:soul_soil',74:'minecraft:magma_block',75:'minecraft:sculk',76:'minecraft:calcite',77:'minecraft:tuff',78:'minecraft:dripstone_block',79:'minecraft:amethyst_block',80:'minecraft:copper_block',81:'minecraft:deepslate',82:'minecraft:mangrove_log',83:'minecraft:cherry_log',84:'minecraft:pointed_dripstone',85:'minecraft:ochre_terracotta',86:'minecraft:pearlescent_terracotta',87:'minecraft:verdant_terracotta',88:'minecraft:froglight',89:'minecraft:mud',90:'minecraft:packed_mud',91:'minecraft:reinforced_deepslate'};
export const NAME2ID={}; Object.entries(ID2NAME).forEach(([k,v])=>NAME2ID[v]=+k);
