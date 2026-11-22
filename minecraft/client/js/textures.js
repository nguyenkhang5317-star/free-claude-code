// Procedural original textures (Appendix F palettes) - canvas atlas 16 tiles
import * as THREE from 'three';
const TILES = ['grass_top','grass_side','dirt','stone','cobble','oak_log_side','oak_log_top','oak_planks','sand','glass','leaves','brick','bedrock','water','lava','gold','diamond','iron','coal','netherrack','end_stone','snow','gravel','oak_sapling','torch','crafting','furnace','bookshelf','glowstone','obsidian','soul_sand','prismarine','sandstone','tnt','pumpkin','melon','wool_white','concrete_white','terracotta','log','planks','stone_bricks','mossy','nether_brick','redstone','lapis','emerald','quartz','clay','ice','sponge','hay','slime','honey','bee_nest','lantern','campfire','composter','barrel','smoker','blast_furnace','grindstone','stonecutter','cartography','fletching','smithing','loom','bell','beacon','conduit','respawn_anchor','crying_obsidian','basalt','blackstone','shroomlight','warped_planks','crimson_planks','ancient_debris','soul_soil','magma','sculk','calcite','tuff','dripstone','amethyst','copper','deepslate','mangrove','cherry','pointed_dripstone','ochre','pearlescent','verdant','froglight','mud','packed_mud','reinforced_deepslate'];
const PAL = {grass_top:[95,159,53],grass_side:[121,85,58],dirt:[134,96,67],stone:[125,125,125],cobble:[110,110,110],oak_log_side:[104,82,50],oak_log_top:[177,144,90],oak_planks:[168,136,82],sand:[219,207,163],glass:[200,230,240],leaves:[56,118,38],brick:[150,97,83],bedrock:[85,85,85],water:[52,95,218],lava:[255,106,0],gold:[252,238,75],diamond:[93,236,231],iron:[216,175,147],coal:[35,35,35],netherrack:[112,58,58],end_stone:[219,219,164],snow:[240,251,251],gravel:[136,126,126]};
function hash(s){let h=0;for(let i=0;i<s.length;i++)h=(h*31+s.charCodeAt(i))|0;return Math.abs(h)}
function tileColor(name){
  if(PAL[name]) return PAL[name];
  const h=hash(name); return [80+(h%120),80+((h>>3)%120),80+((h>>6)%120)];
}
export function makeAtlas(){
  const S=16, cols=16, rows=Math.ceil(TILES.length/cols);
  const c=document.createElement('canvas'); c.width=cols*S; c.height=rows*S;
  const g=c.getContext('2d');
  TILES.forEach((name,idx)=>{
    const x=(idx%cols)*S, y=Math.floor(idx/cols)*S;
    const [r,gg,b]=tileColor(name); const h=hash(name);
    g.fillStyle=`rgb(${r},${gg},${b})`; g.fillRect(x,y,S,S);
    // noise speckles
    for(let i=0;i<28;i++){ const px=x+(h>>i)%S, py=y+(h>>(i+3))%S; g.fillStyle=`rgba(${Math.max(0,r-30)},${Math.max(0,gg-30)},${Math.max(0,b-30)},0.5)`; g.fillRect(px,py,2,2); }
    if(name==='grass_side'){ g.fillStyle='rgb(95,159,53)'; g.fillRect(x,y,16,5); }
    if(name==='glass'){ g.clearRect(x,y,S,S); g.strokeStyle='rgba(200,230,240,0.9)'; g.strokeRect(x+0.5,y+0.5,15,15); }
    if(name==='leaves'){ g.fillStyle='rgba(0,0,0,0.3)'; for(let i=0;i<8;i++) g.fillRect(x+(h>>i)%14,y+(h>>(i+2))%14,3,3); }
    if(name==='brick'){ g.strokeStyle='rgba(0,0,0,0.4)'; g.strokeRect(x,y,16,16); g.beginPath(); g.moveTo(x,y+8); g.lineTo(x+16,y+8); g.stroke(); }
    if(name==='tnt'){ g.fillStyle='#fff'; g.fillRect(x,y+6,16,4); }
    if(name==='torch'){ g.fillStyle='#8b5a2b'; g.fillRect(x+7,y+4,2,12); g.fillStyle='#ffcc33'; g.fillRect(x+6,y,4,4); }
    if(name==='water'||name==='lava'){ g.fillStyle='rgba(255,255,255,0.25)'; g.fillRect(x,y+4,16,2); g.fillRect(x,y+10,16,2); }
  });
  const tex=new THREE.CanvasTexture(c); tex.magFilter=THREE.NearestFilter; tex.minFilter=THREE.NearestFilter; tex.colorSpace=THREE.SRGBColorSpace;
  const uv={}; TILES.forEach((n,i)=>{ uv[n]=[(i%cols)/cols, 1-(Math.floor(i/cols)+1)/rows, 1/cols, 1/rows]; });
  return {tex, uv};
}
export const TILE_LIST=TILES;
