/* App-owned timer presentation. Recipe plans supply targets; methods never invent them. */
'use strict';
const BrewTimers = (() => {
  const methods = Object.freeze({chemex:'Chemex',espresso:'Espresso',cold:'Cold brew',v60:'Hario V60',pulsar:'NextLevel Pulsar',aeropress:'AeroPress','french-press':'French press',clever:'Clever Dripper','hario-switch':'Hario Switch','kalita-wave':'Kalita Wave',melitta:'Melitta-style pour-over','automatic-drip':'Automatic drip','moka-pot':'Moka pot',turkish:'Turkish coffee',phin:'Vietnamese phin',siphon:'Siphon',percolator:'Percolator'});
  function name(live, recipe) { return live.recipe==='timer'?methods[live.method]:recipe?.name||methods[live.recipe]; }
  function identity(live) { return live.id || live.recipe+':'+live.origin+':'+(live.alertRevision||1); }
  function elapsed(live, now=Date.now()) { return live.unknown||!Number.isFinite(live.origin)?null:Math.max(0,Math.floor(((live.stoppedAt??now)-live.origin+(live.offset||0))/1000)); }
  function describe(live, recipe, now=Date.now()) {
    const seconds=elapsed(live,now), custom=live.recipe==='timer', cold=!custom&&live.recipe==='cold'&&live.index===3;
    const stage=recipe?.steps[live.index];
    const target=custom?live.customSeconds:cold?live.durationSeconds:stage?.wait||null;
    const due=target&&seconds!==null?live.origin+target*1000-(live.offset||0):null;
    const reached=due!==null&&now>=due&&!live.complete&&!live.stoppedAt;
    const stageName=custom?'Custom timer':cold?'Steeping':live.paused?'Guide paused':stage?.short||'Brewing';
    let cue=null;
    if(reached&&live.remind!==false&&!live.paused)cue={
      key:identity(live)+'/'+live.index+'/'+(live.alertRevision||1)+'/'+due,
      recipe:live.recipe,session:identity(live),
      title:cold?'Cold brew is ready to filter':custom?name(live,recipe)+' · Timer target reached':name(live,recipe)+' · '+(live.recipe==='chemex'?'Bloom time reached':'Step time reached'),
      body:cold?'Separate the grounds, then refrigerate the concentrate.':custom?'Your chosen time has elapsed. Check your recipe before continuing.':live.recipe==='chemex'?'Continue with the next pour when you’re ready.':'Open the current step before continuing.',
      action:cold?'Open timer':'Return to brew'
    };
    return {name:name(live,recipe),stage:live.paused?'Guide paused':reached?(cold?'Ready to filter':custom?'Timer target reached':live.recipe==='chemex'?'Bloom time reached':'Step time reached'):stageName,seconds,target,due,remaining:due===null?null:Math.max(0,Math.ceil((due-now)/1000)),ready:reached,cue,clockName:custom?'since timer started':recipe?.clockName||'since brewing started',hint:live.recipe==='espresso'&&!live.stoppedAt?'Stop at '+recipe.quantity+' in cup':null};
  }
  function validCustom(live) {
    return !!live&&live.recipe==='timer'&&Object.hasOwn(methods,live.method)&&Number.isInteger(live.customSeconds)&&live.customSeconds>=1&&live.customSeconds<=604800;
  }
  function delivered(live,cue,surface) {
    const value=live[surface==='app'?'seenCues':'notifiedCues'],keys=Array.isArray(value)?value:[];
    if(keys.includes(cue.key))return true;
    // Keep reminders from the previous cold-only preview acknowledged during migration.
    return live.recipe==='cold'&&(surface==='app'?live.inAppAlertedRevision:live.alertedRevision)===live.alertRevision;
  }
  function acknowledge(live,cue,surface) {
    const field=surface==='app'?'seenCues':'notifiedCues',keys=Array.isArray(live[field])?live[field]:[];live[field]=[...keys.filter(key=>key!==cue.key),cue.key].slice(-32);
    if(live.recipe==='cold')live[surface==='app'?'inAppAlertedRevision':'alertedRevision']=live.alertRevision;
  }
  return {methods,name,identity,elapsed,describe,validCustom,delivered,acknowledge};
})();
if(typeof module!=='undefined')module.exports=BrewTimers;
