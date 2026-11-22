// WebAudio synth - sound IDs category.subject.action (§11)
let ctx=null, muted=false;
export function initAudio(){ if(!ctx) ctx=new (window.AudioContext||window.webkitAudioContext)(); }
export function setMuted(m){muted=m}
function tone(freq,dur,type='sine',vol=0.15,slide=0){ if(!ctx||muted)return; const o=ctx.createOscillator(),g=ctx.createGain(); o.type=type; o.frequency.value=freq; if(slide)o.frequency.exponentialRampToValueAtTime(Math.max(30,freq+slide),ctx.currentTime+dur); g.gain.setValueAtTime(vol,ctx.currentTime); g.gain.exponentialRampToValueAtTime(0.001,ctx.currentTime+dur); o.connect(g).connect(ctx.destination); o.start(); o.stop(ctx.currentTime+dur); }
function noise(dur=0.1,vol=0.2,freq=1000){ if(!ctx||muted)return; const b=ctx.createBuffer(1,ctx.sampleRate*dur,ctx.sampleRate); const d=b.getChannelData(0); for(let i=0;i<d.length;i++)d[i]=(Math.random()*2-1)*(1-i/d.length); const s=ctx.createBufferSource(); s.buffer=b; const f=ctx.createBiquadFilter(); f.type='bandpass'; f.frequency.value=freq; const g=ctx.createGain(); g.gain.value=vol; s.connect(f).connect(g).connect(ctx.destination); s.start(); }
export const SFX={
  'block.grass.break':()=>noise(0.15,0.3,600),
  'block.stone.break':()=>noise(0.2,0.35,400),
  'block.wood.break':()=>noise(0.2,0.3,300),
  'block.grass.place':()=>noise(0.08,0.2,700),
  'block.stone.place':()=>tone(180,0.08,'square',0.12),
  'block.wood.place':()=>tone(220,0.08,'square',0.12),
  'entity.player.hurt':()=>tone(300,0.15,'sawtooth',0.15,-150),
  'entity.zombie.hurt':()=>tone(150,0.2,'sawtooth',0.15,-50),
  'entity.cow.hurt':()=>tone(400,0.15,'square',0.12,-100),
  'entity.skeleton.hurt':()=>noise(0.1,0.2,1200),
  'entity.player.levelup':()=>{tone(660,0.1);setTimeout(()=>tone(880,0.15),90)},
  'entity.experience_orb.pickup':()=>tone(900,0.08,'sine',0.15,300),
  'block.anvil.land':()=>tone(120,0.3,'square',0.25,-60),
  'entity.generic.explode':()=>noise(0.6,0.5,200),
  'entity.ender_dragon.growl':()=>tone(80,0.8,'sawtooth',0.3,40),
  'music.menu':()=>tone(440,0.5,'sine',0.08),
  'ui.button.click':()=>tone(600,0.05,'square',0.1),
  'block.note_block.harp':()=>tone(523,0.3,'sine',0.2),
};
export function play(id){ try{ (SFX[id]||(()=>tone(440,0.08)))(); }catch(e){} }
