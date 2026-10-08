'use strict';
const query = new URLSearchParams(location.search);
let reading = {}, locationMemory = {}, sessions = {foreground:null, cold:null}, storageAvailable = true;
let alertPermission = 'allowed', renderedColdReady = false;
let reviewState = 'saved', savedReview = null;
let sessionSequence=0;
function newSessionId(method){return method+'-'+Date.now()+'-'+(++sessionSequence);}
try {
  reading = JSON.parse(localStorage.getItem('starlit-guide-reading')) || {};
  locationMemory = JSON.parse(localStorage.getItem('starlit-guide-location')) || {};
  const saved = JSON.parse(localStorage.getItem('starlit-visual-guide-v2') || localStorage.getItem('starlit-visual-guide-v1'));
  sessions = ColdTimer.restore(saved, Object.fromEntries(Object.entries(recipes).map(([key,r])=>[key,r.steps.length]).concat([["timer",1]])));
  if(sessions.foreground?.recipe==="timer"&&!BrewTimers.validCustom(sessions.foreground))sessions.foreground=null;
  alertPermission = localStorage.getItem('starlit-preview-alert-permission') === 'blocked' ? 'blocked' : 'allowed';
} catch { storageAvailable = false; }
let chosen = recipes[query.get('method')] ? query.get('method') : recipes[locationMemory.method] ? locationMemory.method : 'chemex';
let entry = query.has('entry') ? (query.get('entry') === 'learn' ? 'learn' : 'brew') : (locationMemory.entry === 'learn' ? 'learn' : 'brew');
let area = query.has('entry') ? (entry==='learn'?'learn':'brew') : appAreas.includes(locationMemory.area) ? locationMemory.area : entry==='learn'?'learn':'brew';
let view = 'overview', step = 0, browsing = false;
if (!query.has('method') && !query.has('entry') && locationMemory.reader && Number.isInteger(reading[chosen])) {
  step = Math.max(0,Math.min(reading[chosen],recipes[chosen].steps.length-1)); view='step'; browsing=true;
}
const app = document.getElementById('app'), dialog = document.getElementById('dialog');
const modalLayer = document.getElementById('app-modal-layer');
let modalOpener = null;
document.getElementById('alert-control').value = alertPermission;
const currentBrew = () => view!=='timer'&&chosen === 'cold' ? sessions.cold : sessions.foreground;
const sessionFor = recipe => recipe === 'cold' ? sessions.cold : sessions.foreground?.recipe === recipe ? sessions.foreground : null;
function putSession(value) { markSampleEdited();sessions[chosen === 'cold' ? 'cold' : 'foreground'] = value; }
function markSampleEdited() {
  if(reviewState==='saved'||reviewState==='custom')return;
  reviewState='custom';document.getElementById('review-state').value='custom';
}
function rememberReading() {
  if(view==='step' && browsing) reading[chosen]=step;
  if(reviewState!=='saved')return;
  try { localStorage.setItem('starlit-guide-reading',JSON.stringify(reading)); localStorage.setItem('starlit-guide-location',JSON.stringify({method:chosen,entry,area,reader:(area==='learn'||area==='brew')&&view==='step'&&browsing})); } catch {}
}
function persist() {
  if(reviewState!=='saved')return;
  try { localStorage.setItem('starlit-visual-guide-v2',JSON.stringify({version:2,...sessions})); localStorage.setItem('starlit-preview-alert-permission',alertPermission); storageAvailable=true; }
  catch { storageAvailable=false; }
}
function sampleBrews(state) {
  if(state==='none')return {foreground:null,cold:null};
  const ready=state==='ready',now=Date.now();
  if(['bloom','shot','method-timer'].includes(state)){
    const foreground=state==='method-timer'?{recipe:'timer',method:document.getElementById('timer-method-control').value||'french-press',index:0,customSeconds:60,origin:now-20000,offset:0,stoppedAt:null,paused:false,complete:false,remind:true,alertRevision:1}:{recipe:state==='shot'?'espresso':'chemex',index:4,origin:now-20000,offset:0,stoppedAt:null,paused:false,complete:false,remind:true};
    return {foreground,cold:null};
  }
  const cold={recipe:'cold',index:3,origin:now-(ready?ColdTimer.DEFAULT_SECONDS+60:7200)*1000,offset:0,stoppedAt:null,paused:false,complete:false,quick:true,durationSeconds:ColdTimer.DEFAULT_SECONDS,remind:true,alertRevision:1,alertedRevision:0,inAppAlertedRevision:0};
  const foreground=state==='multiple'?{recipe:'chemex',index:4,origin:now-25000,offset:0,stoppedAt:null,paused:false,complete:false}:null;
  return {foreground,cold};
}
function setReviewState(next) {
  if(!['saved','none','steeping','ready','multiple','bloom','shot','method-timer'].includes(next)||next===reviewState)return;
  closeModal();
  setPreviewSurface('app');
  document.getElementById('sample-method-control').hidden=next!=='method-timer';
  document.getElementById('announcement').textContent='';
  let scrollTop=document.getElementById('screen-content').scrollTop||0;
  if(reviewState==='saved')savedReview={sessions:JSON.parse(JSON.stringify(sessions)),reading:{...reading},alertPermission,appAlert:appAlert?{...appAlert}:null,sessionReturn:sessionReturn?{...sessionReturn}:null,chosen,entry,area,view,step,browsing,scrollTop};
  reviewState=next;
  if(next==='saved'){
    ({sessions,reading,alertPermission,appAlert,sessionReturn,chosen,entry,area,view,step,browsing}=savedReview);
    scrollTop=savedReview.scrollTop;savedReview=null;
    document.getElementById('alert-control').value=alertPermission;
  }else{
    sessions=sampleBrews(next);appAlert=null;sessionReturn=null;
    if((view==='step'&&!browsing)||view==='complete'||view==='timer'){view='overview';browsing=false;scrollTop=0;}
  }
  document.getElementById('review-state').value=next;
  document.getElementById('review-state-note').textContent=next==='saved'?'Your saved preview sessions.':'Sample timers only. Saved sessions and reading positions stay unchanged.';
  render(false);document.getElementById('screen-content').scrollTop=scrollTop;
}
function elapsed(live=currentBrew()) { if(live?.origin == null) return 0; return Math.max(0,Math.floor(((live.stoppedAt ?? Date.now())-live.origin+(live.offset||0))/1000)); }
function time(secs) { if(secs>=3600) return Math.floor(secs/3600)+'h '+String(Math.floor(secs%3600/60)).padStart(2,'0')+'m'; return Math.floor(secs/60)+':'+String(secs%60).padStart(2,'0'); }
function clockText(live=currentBrew()) { return live?.unknown ? 'Time unknown' : time(elapsed(live)); }
function isLive() { const live=currentBrew(); return area==='brew'&&view==='step'&&!browsing&&live&&!live.complete&&live.recipe===chosen; }
function dateLabel(ms) { return new Intl.DateTimeFormat(undefined,{weekday:'short',month:'short',day:'numeric',hour:'numeric',minute:'2-digit'}).format(new Date(ms)); }
function remainingLabel(batch) { const seconds=ColdTimer.remaining(batch); return seconds===null?'Time unknown':seconds===0?'Ready to filter':ColdTimer.label(Math.ceil(seconds/60)*60)+' left'; }
function button(action,label,cls='primary',extra='') { return '<button class="'+cls+'" data-action="'+action+'" '+extra+'>'+label+'</button>'; }
function overview() {
  const r=recipes[chosen],resume=Number.isInteger(reading[chosen])&&reading[chosen]>0&&reading[chosen]<r.steps.length?reading[chosen]:0,exploreLabel=resume?'Continue · '+r.steps[resume].short:'Explore the steps';
  return '<div class="content"><p class="eyebrow">'+(entry==='learn'?'Learn at your own pace':'Your next brew')+'</p><div class="profile-heading"><div class="profile-badge">'+equipmentIcon(chosen)+'</div><div><h1 tabindex="-1">'+r.name+'</h1><p class="intro-sub">'+r.subtitle+'</p></div></div><figure class="illustration" style="margin-left:0;margin-right:0">'+art(chosen,r.overviewArt,r.type)+'</figure><div class="metrics"><div class="metric"><strong>'+r.dose+'</strong><span>dry coffee</span></div><div class="metric"><strong>'+r.quantity+'</strong><span>'+r.basis+'</span></div></div><div class="facts"><span class="pill">'+r.grind+'</span><span class="pill">'+r.temp+'</span></div>'+'<div class="journey-heading"><h2>The shape of the brew</h2>'+button('allsteps','All '+r.steps.length+' steps','text-button')+'</div><div class="journey">'+r.teaser.map(i=>'<button data-action="explore" data-value="'+i+'">'+art(chosen,r.steps[i].art,'',true)+'<span>'+r.steps[i].short+'</span></button>').join('')+'</div><details><summary>Recipe & equipment</summary><div class="explanation">'+r.provenance+'. '+(chosen==='chemex'?'Matching six-cup bonded paper, scale, kettle, grinder and spoon. Rinse water is separate. Finish by drainage, with no fixed total time.':chosen==='espresso'?'Bambino Plus BES500, supplied 54 mm two-cup single-wall basket, tamper, grinder, 0.1 g scale. The yield is beverage mass; machine reservoir fill is separate.':'Clean covered vessel around 1.5 L with stirring room, thermometer, scale, sieve and supported paper filter. Fourteen hours refrigerated, then untimed filtration; dilute by measured mass.')+'<br><a href="'+r.source+'" target="_blank" rel="noopener">'+r.sourceName+' ↗</a></div></details></div><footer class="bottom">'+(entry==='brew'?button('start','Prepare to brew '+icons.forward)+button('explore',exploreLabel,'text-button'):button('explore',exploreLabel+' '+icons.forward)+button('start','Brew this','text-button'))+'</footer>';
}
function allsteps() { const r=recipes[chosen],live=currentBrew(); return '<div class="content"><p class="eyebrow">'+(live&&!live.complete?'Read ahead':'The whole guide')+'</p><h1 tabindex="-1" style="margin-top:10px">'+r.name+', step by step.</h1><p class="intro-sub">Explore any step. '+(live&&!live.complete?'Your brew keeps its place.':'No timer starts.')+'</p><div class="stage-list">'+r.steps.map((s,i)=>'<button class="stage-row" data-action="explore" data-value="'+i+'"><span class="row-num">'+String(i+1).padStart(2,'0')+'</span>'+art(chosen,s.art,'',true)+'<span><span class="row-title">'+s.short+'</span><small>'+s.phase+'</small></span></button>').join('')+'</div></div><footer class="bottom">'+button('overview','Back to overview','secondary')+'</footer>'; }
function target(s) { return s.target?'<div class="targets"><div><div class="target-label">'+s.targetLabel+'</div><div class="big '+(!s.unit&&chosen==='cold'?'duration-value':'')+'">'+s.target+' <span class="unit">'+s.unit+'</span></div></div>'+(s.side?'<div class="secondary-target"><b>'+s.side+'</b>'+s.sideLabel+'</div>':'')+'</div>':''; }
function durationFields(batch) {
  const seconds=batch?.durationSeconds??ColdTimer.DEFAULT_SECONDS;
  return '<fieldset class="duration-fields"><legend>Steep duration</legend><div><label for="timer-hours">Hours</label><input id="timer-hours" class="late-input" type="number" min="0" max="168" step="1" inputmode="numeric" value="'+Math.floor(seconds/3600)+'"></div><div><label for="timer-minutes">Minutes</label><input id="timer-minutes" class="late-input" type="number" min="0" max="59" step="1" inputmode="numeric" value="'+Math.floor(seconds%3600/60)+'"></div></fieldset><label class="reminder-check"><span>Remind me when it’s ready to filter</span><span class="switch-control"><input type="checkbox" role="switch" id="timer-remind" '+(batch?.remind===false?'':'checked')+'><span class="switch-track" aria-hidden="true"></span></span></label><p id="timer-error" class="note" role="alert"></p>';
}
function coldStartScreen() { return '<div class="content cold-start"><p class="eyebrow">Your own recipe</p><h1 tabindex="-1" style="margin-top:12px">Start a cold brew timer</h1><p class="intro-sub">Start when the covered batch goes into the refrigerator.</p>'+durationFields(null)+'<p class="note">14 h is the guide’s starting recipe. Choose the duration for your batch.</p><details><summary>Already steeping?</summary><label class="late-label" for="timer-start">Refrigerated at</label><input id="timer-start" class="late-input" type="datetime-local"><p class="note">Use the actual start. Leave empty to start now.</p>'+button('quick-unknown','Start time unknown','text-button')+'</details><div class="safety">'+icons.warning+'<span>Keep at 4 °C or colder throughout. Time does not prove safety.</span></div></div><footer class="bottom">'+button('quick-save','Start timer')+button('overview','Go back','text-button')+'</footer>'; }
function reminderStatus(batch) { return batch.remind===false?'Off':batch.unknown?'Start unknown':alertPermission==='blocked'?'Notifications off':'On'; }
icons.schedule='<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M11.99 2A10 10 0 1 0 12 22 10 10 0 0 0 11.99 2zM12 20a8 8 0 1 1 0-16 8 8 0 0 1 0 16zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67V7z"/></svg>';
icons.chevron='<svg class="row-chevron" viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="m9.29 6.71 1.42-1.42L17.42 12l-6.71 6.71-1.42-1.42L14.58 12z"/></svg>';
function timerReadout(batch) {
  const left=ColdTimer.remaining(batch);
  if(left===null)return '<span class="time-unknown">Time unknown</span>';
  const seconds=ColdTimer.ready(batch)?batch.durationSeconds:Math.ceil(left/60)*60;
  const hours=Math.floor(seconds/3600),minutes=Math.floor(seconds%3600/60);
  return (hours?'<span class="time-part"><b>'+hours+'</b><span class="time-unit">h</span></span>':'')+(minutes||!hours?'<span class="time-part"><b>'+minutes+'</b><span class="time-unit">min</span></span>':'');
}
function coffeeProgress(percent,unknown) {
  const bean='<svg class="coffee-bean" viewBox="0 0 24 28" aria-hidden="true" style="left:'+percent+'%"><path class="bean-body" d="M18.8 2.6C13.3-.4 6 3.2 3.5 9.8S3.4 22.6 8.7 25.3c5.3 2.7 12.5-1.2 14-7.7s1.5-12-3.9-15Z"/><path class="bean-seam" d="M17.5 4.8c-6.5 2.4-2.4 7.2-7.6 10.8-3.2 2.2-1.6 5.1-1.1 7"/></svg>';
  return '<div class="coffee-progress cold-progress" '+(unknown?'hidden':'')+' role="progressbar" aria-label="Steep progress" aria-valuemin="0" aria-valuemax="100" aria-valuenow="'+Math.round(percent)+'"><div class="coffee-rail"><span style="width:'+percent+'%"></span>'+bean+'</div></div>';
}
function coldWaitScreen() {
  const batch=sessions.cold,ready=ColdTimer.ready(batch),due=ColdTimer.deadline(batch),left=ColdTimer.remaining(batch);
  const percent=left===null?0:Math.min(100,Math.max(0,(1-left/batch.durationSeconds)*100));
  const reminderAction=alertPermission==='blocked'&&batch.remind?'enable-alerts':'edit-timer';
  return '<div class="content cold-wait '+(ready?'steep-ready':'')+'"><section class="steep-hero"><div class="steep-symbol" aria-hidden="true">'+equipmentIcon('cold')+(ready?'<span class="ready-check">'+icons.check+'</span>':'')+'</div><h1 tabindex="-1">'+(batch.unknown?'Start time unknown':ready?'Ready to filter':'Steeping')+'</h1><p class="hero-caption">'+(ready?'Steep target':'Time left')+'</p><div class="expressive-time" data-cold-hero role="timer" aria-live="off" aria-label="'+esc(ready?'Steep target '+ColdTimer.label(batch.durationSeconds):remainingLabel(batch))+'">'+timerReadout(batch)+'</div><p class="timer-due" data-cold-due>'+(due===null?'Enter the actual start to set the finish.':(ready?'Target was ':'Ready ')+dateLabel(due))+'</p></section>'+coffeeProgress(percent,batch.unknown)+'<div class="timer-meta"><span>'+icons.check+'<span>'+(storageAvailable?'Timer saved':'Timer not saved')+'</span></span><span class="pill">Refrigerated · ≤4 °C</span></div><p class="timer-guidance">'+(batch.unknown?'The finish stays unknown until you enter the actual start.':ready?'Separate the grounds, then refrigerate the concentrate promptly.':'Keep the batch covered and refrigerated.')+'</p><div class="timer-settings">'+button('edit-timer','<span class="setting-icon">'+icons.schedule+'</span><span class="setting-label">Steep duration</span><span class="setting-value">'+ColdTimer.label(batch.durationSeconds)+'</span>'+icons.chevron,'setting-row','aria-label="Change steep duration"')+button(reminderAction,'<span class="setting-icon">'+icons.bell+'</span><span class="setting-label">Ready reminder</span><span class="setting-value" data-reminder-status>'+reminderStatus(batch)+'</span>'+icons.chevron,'setting-row',reminderAction==='enable-alerts'?'aria-label="Ready reminder: enable notifications"':'aria-label="Change ready reminder"')+'<details><summary>Timer details</summary><div class="explanation">'+(batch.origin===null?'Start time unknown.':'Started '+dateLabel(batch.origin-(batch.offset||0))+'.')+'<br>'+(batch.quick?'Your own recipe; the guide’s quantities and preparation are not assumed.':batch.durationSeconds===ColdTimer.DEFAULT_SECONDS?'Reviewed 14-hour refrigerated recipe.':'Duration adjusted for this brew. The reviewed guide remains 14 hours.')+'<br>Time and appearance do not establish safety. Keep at 4 °C or colder throughout.'+button('end','End timer','text-button')+'</div></details></div></div><footer class="bottom">'+(batch.unknown?button('cold-late','Enter start time'):ready?button(batch.quick?'quick-filtered':'advance',batch.quick?'I’ve filtered this batch':'Continue to filtering'):button('overview','Done for now'))+(batch.quick?button('filter-help','See how to filter','text-button'):'')+'</footer>';
}
function stageScreen() {
  const live=currentBrew(),r=recipes[chosen],active=isLive(),idx=active?live.index:step;
  if(active&&chosen==='cold'&&idx===3) return coldWaitScreen();
  let s=r.steps[idx];
  if(active&&chosen==='cold'&&idx===r.startIndex) s={...s,target:ColdTimer.label(live.durationSeconds),unit:'',targetLabel:'Steep duration'};
  const running=active&&(live.origin!=null||live.unknown),waiting=active&&s.wait,readyToStart=active&&idx===r.startIndex&&live.origin==null&&!live.unknown;
  let banner='';
  if(active&&live.paused) banner='<div class="pause-banner"><b>Guide paused</b>'+((live.origin!=null||live.unknown)&&!live.stoppedAt?(live.unknown?'Coffee is still extracting. Start time is unknown.':'Coffee is still extracting. The clock continues.'):'Your step is saved.')+'</div>';
  const clock=running?'<div class="clockline"><span><span class="dot"></span>'+(live.stoppedAt?'Recorded time':esc(r.clockName))+'</span><strong data-clock="'+chosen+'">'+clockText()+'</strong></div>':'';
  const body='<div class="content stepscreen"><div class="step-top"><p class="eyebrow">'+(active?s.phase:'Explore')+' · '+(idx+1)+' / '+r.steps.length+'</p>'+button('allsteps',active?'Look ahead':'All steps','text-button')+'</div><div class="progress" aria-hidden="true"><span style="width:'+((idx+1)/r.steps.length*100)+'%"></span></div><h1 class="step-title" tabindex="-1">'+esc(s.title).replace(/\b(240|480) g\b/g,'<span style="white-space:nowrap">$1 g</span>')+'</h1>'+target(s)+'<div class="illustration">'+art(chosen,s.art,s.body+' Done when: '+s.cue)+'</div><p class="instruction">'+s.body+'</p>'+(active&&live.early&&chosen==='espresso'?'<div class="safety">'+icons.warning+'<span>The preset stopped early. Do not restart this shot.</span></div>':'')+'<div class="done-cue">'+icons.check+'<span><b>Done when</b> · '+s.cue+'</span></div>'+(s.safety?'<div class="safety">'+icons.warning+'<span>'+s.safety+'</span></div>':'')+clock+(readyToStart?'<p class="note">'+s.origin+' The clock has not started.</p>':'')+(readyToStart&&chosen==='cold'?button('edit-timer','Change timer','text-button'):'')+(!active&&s.wait?'<p class="note">Recipe timing: '+(chosen==='cold'?'14 hours from refrigeration.':'0:45 from the first water, including pouring.')+' No clock runs while you explore.</p>':'')+(active&&running?button('reminder-options','<span class="setting-icon">'+icons.bell+'</span> Step reminders · '+reminderStatus(live),'text-button'):'')+'<details><summary>Why this step?</summary><div class="explanation">'+s.more+'<br><a href="'+r.source+'" target="_blank" rel="noopener">Recipe & sources ↗</a></div></details></div>';
  let actions='';
  if(!active) actions='<div class="step-controls">'+button('previous',icons.back,'previous','aria-label="Previous step" '+(idx===0?'disabled':''))+button(idx===r.steps.length-1?'overview':'next',idx===r.steps.length-1?'Back to overview':'Next step '+icons.forward)+'</div><p class="no-timer">'+(browsing&&live&&!live.complete?'Reading only · your brew keeps its place':'Explore freely · no timer')+'</p>';
  else if(live.paused) actions=button('resume','Resume guide');
  else if(waiting&&live.unknown) actions='<p class="note">Start time unknown. The 0:45 bloom target cannot be checked. Enter the actual start if you can establish it; otherwise end this brew.</p>'+button('late','Enter known start time')+button('end','End this brew','text-button');
  else if(readyToStart) actions=button('start-clock',s.start)+button(chosen==='cold'?'cold-late':'late','Already started?','text-button');
  else { const disabled=waiting&&elapsed()<s.wait; actions=button('advance',disabled?'Waiting for 0:45':s.action,'primary','id="advance" '+(disabled?'disabled':''))+(s.stopAlternative?button('early-stop',s.stopAlternative,'text-button'):'')+button('pause','Pause guide','text-button'); }
  return banner+body+'<footer class="bottom">'+actions+'</footer>';
}
function completed() {
  const r=recipes[chosen],live=currentBrew();
  if(chosen==='cold'&&live?.quick) return '<div class="content"><div class="success" aria-hidden="true">'+icons.check+'</div><h1 tabindex="-1">Timer finished</h1><p class="complete-copy">You confirmed that this batch is filtered. Keep the concentrate covered and refrigerated.</p><p class="note">No recipe quantities or final yield were recorded.</p></div><footer class="bottom">'+button('overview','Done')+button('quick-timer','Start another timer','text-button')+'</footer>';
  return '<div class="content"><div class="success" aria-hidden="true">'+icons.check+'</div><p class="eyebrow">Brew complete</p><h1 tabindex="-1" style="margin-top:12px">Enjoy your '+(chosen==='espresso'?'espresso.':'coffee.')+'</h1><p class="complete-copy">'+(chosen==='cold'?'Concentrate and serving water stay separate. Keep the rest covered and refrigerated.':chosen==='espresso'?'Taste once it cools. Final yield comes from your scale.':'Swirl gently, then pour. Use the collar or handle.')+'</p><div class="illustration">'+art(chosen,chosen==='chemex'?'serve':chosen==='espresso'?'cup':'dilute','Finished '+r.name)+'</div><div class="metrics"><div class="metric"><strong>'+r.dose+'</strong><span>recipe dose</span></div><div class="metric"><strong>'+clockText()+'</strong><span>'+(chosen==='cold'?'refrigerated steep':chosen==='espresso'?'pump running':'first water to drainage')+'</span></div></div><p class="note">Targets and confirmations only. No actual beverage mass was recorded.'+(live?.early?' The preset stopped early. Do not restart this shot.':'')+'</p><details><summary>Next time & cleanup</summary><div class="explanation">'+(chosen==='chemex'?'If flow was slow, check paper and spout first; try slightly coarser next time. Let glass cool before washing; remove the collar and tie.':chosen==='espresso'?'Keep dose and yield fixed while adjusting grind. If the preset stopped early, follow the manual’s programming instructions next brew. Use handles; unplug and cool before further cleaning.':'Adjust dilution before changing extraction. Clean equipment promptly. This recipe establishes no maximum safe storage period.')+'</div></details></div><footer class="bottom">'+button('overview','Done')+button('start','Brew again','text-button')+'</footer>';
}
function render(focus=true) {
  rememberReading(); renderedColdReady=ColdTimer.ready(sessions.cold);
  const guide=area==='brew'||area==='learn',landing=guide&&view==='overview';
  const screen=view==='timer'?customTimerScreen():landing?(area==='brew'?nativeCalculator():nativeLibrary()):guide?(view==='guide'?overview():view==='allsteps'?allsteps():view==='complete'?completed():view==='cold-start'?coldStartScreen():stageScreen()):otherAreaScreen();
  app.innerHTML=appHeader()+'<div id="app-alert"></div><section id="screen-content" aria-label="'+nativeTitles[area]+'" class="'+(landing&&area==='brew'?'calculator-host':area==='favorites'||area==='beans'?'empty-host':'')+'">'+screen+'</section><div class="app-dock">'+globalBrews()+appNavigation()+'</div>';
  document.querySelectorAll('#entry-controls button').forEach(b=>{b.classList.toggle('selected',b.dataset.value===entry);b.setAttribute('aria-pressed',b.dataset.value===entry)});
  document.querySelectorAll('#method-controls button').forEach(b=>{b.classList.toggle('selected',b.dataset.value===chosen);b.setAttribute('aria-pressed',b.dataset.value===chosen)});
  updateClock();
  updateIntegrationContext();
  if(focus){app.querySelector('h1')?.focus({preventScroll:true});app.scrollIntoView({block:'start',behavior:'instant'});}
}
function updateClock() {
  const cold=sessions.cold;
  for(const live of activeBrews()){
    const cue=timerDescription(live).cue;if(!cue)continue;
    if(previewSurface==='app'&&!dialog.open&&!BrewTimers.delivered(live,cue,'app')){
      BrewTimers.acknowledge(live,cue,'app');persist();
      if(!(view==='timer'&&live.recipe==='timer'||isLive()&&currentBrew()===live))appAlert=cue;
      document.getElementById('announcement').textContent=cue.title;
    }else if(previewSurface!=='app'&&alertPermission==='allowed'&&!BrewTimers.delivered(live,cue,'app')&&!BrewTimers.delivered(live,cue,'os')){
      BrewTimers.acknowledge(live,cue,'os');persist();
      document.getElementById('announcement').textContent=cue.title;
    }
  }
  if(renderedColdReady!==ColdTimer.ready(cold)&&!dialog.open) {
    renderedColdReady=ColdTimer.ready(cold);
    if(isLive()&&chosen==='cold'&&cold.index===3){render(false);return;}
  }
  document.querySelectorAll('[data-clock]').forEach(el=>el.textContent=clockText(sessionFor(el.dataset.clock)));
  document.querySelectorAll('[data-cold-remaining]').forEach(el=>el.textContent=remainingLabel(cold));
  document.querySelectorAll('[data-cold-hero]').forEach(el=>{const markup=timerReadout(cold);if(el.innerHTML!==markup)el.innerHTML=markup;el.setAttribute('aria-label',ColdTimer.ready(cold)?'Steep target '+ColdTimer.label(cold.durationSeconds):remainingLabel(cold));});
  document.querySelectorAll('[data-cold-status]').forEach(el=>el.textContent=ColdTimer.ready(cold)?'Cold brew ready to filter':'Cold brew steeping');
  if(cold){ const due=ColdTimer.deadline(cold),left=ColdTimer.remaining(cold),pct=left===null?0:Math.min(100,Math.max(0,(1-left/cold.durationSeconds)*100)); document.querySelectorAll('.cold-progress').forEach(el=>{el.setAttribute('aria-valuenow',String(Math.round(pct)));el.querySelector('span').style.width=pct+'%';el.querySelector('.coffee-bean').style.left=pct+'%'}); document.querySelectorAll('[data-reminder-status]').forEach(el=>el.textContent=reminderStatus(cold)); document.querySelectorAll('[data-cold-due]').forEach(el=>el.textContent=due===null?'Enter the actual start to set the finish.':(ColdTimer.ready(cold)?'Target was ':'Ready ')+dateLabel(due)); }
  const live=currentBrew(),r=live?recipes[live.recipe]:null,s=r?.steps[live.index],b=document.getElementById('advance');
  if(b&&s?.wait&&!live.unknown&&elapsed()>=s.wait&&!live.paused){b.disabled=false;b.textContent=s.action;}
  document.getElementById('sim-status').textContent=!storageAvailable?'Browser storage unavailable; this preview lasts only while open.':[sessions.foreground,sessions.cold].filter(Boolean).map(x=>timerName(x)+': '+(x.complete?'finished':x.unknown?'time unknown':x.origin!=null?x.recipe==='cold'&&x.index===3?remainingLabel(x):clockText(x):'preparing')).join(' · ')||'No live brew.';
  document.querySelectorAll('[data-action="time"]').forEach(b=>b.disabled=![sessions.foreground,sessions.cold].some(x=>x?.origin!=null&&!x.stoppedAt&&!x.complete));
  if(view==='timer'&&sessions.foreground?.recipe==='timer'){
    const d=timerDescription(sessions.foreground);
    document.querySelectorAll('[data-custom-time]').forEach(el=>el.textContent=d.remaining===null?'Time unknown':time(d.remaining));
    document.querySelectorAll('[data-custom-stage]').forEach(el=>el.textContent=d.stage);
  }
  updateAppChrome();updateNotificationSurface();
}
function closeModal() {
  if(!dialog.open)return;
  dialog.close();modalLayer.hidden=true;app.inert=false;
  const target=modalOpener?.isConnected?modalOpener:app.querySelector('h1');
  modalOpener=null;target?.focus({preventScroll:true});
}
function modal(title,body,actions) {
  if(!dialog.open)modalOpener=document.activeElement;
  dialog.dataset.kind='';dialog.innerHTML='<h2 id="dialog-title">'+title+'</h2>'+(body?'<p>'+body+'</p>':'')+actions;
  modalLayer.hidden=false;app.inert=true;
  if(!dialog.open)dialog.show();
  dialog.scrollTop=0;modalLayer.scrollIntoView({block:'nearest',inline:'nearest'});
  dialog.querySelector('button:not([disabled]),input:not([disabled]),select:not([disabled])')?.focus({preventScroll:true});
}
document.addEventListener('keydown',event=>{
  if(!dialog.open||!dialog.contains(document.activeElement))return;
  if(event.key==='Escape'){event.preventDefault();closeModal();updateClock();return;}
  if(event.key!=='Tab')return;
  const controls=Array.from(dialog.querySelectorAll('button:not([disabled]),input:not([disabled]),select:not([disabled]),a[href],[tabindex="0"]')).filter(el=>el.getClientRects().length);
  const first=controls[0],last=controls[controls.length-1];
  if(event.shiftKey&&document.activeElement===first){event.preventDefault();last?.focus();}
  else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first?.focus();}
});
function start() {
  const live=currentBrew();
  if(live&&!live.complete){modal('Your '+timerName(live)+' is in progress.','Return to the current step. A new '+(chosen==='cold'?'cold brew':'brew')+' replaces this session.',button('return-live','Return to brew')+button('replace','End this brew and start another','secondary')+button('close-dialog','Keep browsing','text-button'));return;}
  if(chosen==='cold'){modal('Start cold brew','Follow the recipe, or set a reminder for a batch you’ve already prepared.',button('start-guide','Follow the guide')+button('quick-timer','Just set a timer','secondary')+button('close-dialog','Go back','text-button'));return;}
  newBrew();
}
function newBrew() { area='brew';entry='brew';sessionReturn=null;putSession({id:newSessionId(chosen),recipe:chosen,index:0,origin:null,offset:0,stoppedAt:null,paused:false,complete:false,remind:true,...(chosen==='cold'?{durationSeconds:ColdTimer.DEFAULT_SECONDS,quick:false,remind:true,alertRevision:1,alertedRevision:0,inAppAlertedRevision:0}:{})});view='step';browsing=false;persist();render(); }
function clockStart(seconds=0) { const live=currentBrew(),r=recipes[chosen]; markSampleEdited();live.unknown=seconds===null;live.origin=seconds===null?null:Date.now()-seconds*1000;live.offset=0;live.paused=false;if(chosen==='cold'){live.alertRevision++;}if(live.index===r.startIndex&&r.steps[live.index].startAdvance)live.index++;persist();render(); }
function advance() { const live=currentBrew(),r=recipes[live.recipe],s=r.steps[live.index];if(live.paused||s.wait&&(chosen==='cold'?!ColdTimer.ready(live):live.unknown||elapsed()<s.wait))return;markSampleEdited();if(live.index===r.stopIndex){live.stoppedAt=Date.now();}if(live.index===r.steps.length-1){live.complete=true;live.paused=false;view='complete';if(!live.stoppedAt)live.stoppedAt=Date.now();}else live.index++;persist();render();document.getElementById('announcement').textContent=live.complete?'Brew complete.':r.steps[live.index].title; }
function openQuickTimer() { const cold=sessions.cold; if(cold&&!cold.complete){modal('A cold brew timer is already saved.','View the batch already steeping, or end its timer before starting another.',button('return-session','View timer','primary','data-value="cold"')+button('replace-quick','End timer and start another','secondary')+button('close-dialog','Go back','text-button'));return;}area='brew';entry='brew';view='cold-start';browsing=false;render(); }
function readDuration() { const seconds=ColdTimer.duration(document.getElementById('timer-hours').value,document.getElementById('timer-minutes').value);if(seconds===null){document.getElementById('timer-error').textContent='Use whole hours and 0–59 minutes, from 1 minute to 7 days. This input limit is not a brewing recommendation.';document.getElementById('timer-hours').focus();}return seconds; }
function quickSave(unknown=false) {
  const seconds=readDuration();if(seconds===null)return;
  const startValue=document.getElementById('timer-start').value,origin=unknown?null:startValue?new Date(startValue).getTime():Date.now();
  if(!unknown&&(!Number.isFinite(origin)||origin>Date.now())){document.getElementById('timer-error').textContent='Enter an actual start time that is not in the future.';document.getElementById('timer-start').focus();return;}
  markSampleEdited();area='brew';entry='brew';sessions.cold={id:newSessionId('cold'),recipe:'cold',index:3,origin,unknown,offset:0,stoppedAt:null,paused:false,complete:false,quick:true,durationSeconds:seconds,remind:document.getElementById('timer-remind').checked,alertRevision:1,alertedRevision:0,inAppAlertedRevision:0};view='step';browsing=false;persist();render();
}
function coldLate() { modal('When did you refrigerate it?','Enter when the fully wetted, covered batch went into the refrigerator. The timer uses that actual start.','<label class="late-label" for="cold-start-time">Refrigerated at</label><input id="cold-start-time" class="late-input" type="datetime-local"><p id="late-error" role="alert"></p>'+button('cold-late-save','Use this start')+button('unknown-time','I don’t know the time','secondary')+button('close-dialog','Go back','text-button')); }
document.getElementById('theme-control').addEventListener('change',e=>{document.documentElement.dataset.theme=e.target.value;});
document.getElementById('review-state').addEventListener('change',e=>setReviewState(e.target.value));
document.getElementById('alert-control').addEventListener('change',e=>{alertPermission=e.target.value;persist();render(false);});
document.addEventListener('click',e=> {
  const b=e.target.closest('button[data-action]');if(!b||b.disabled)return;const a=b.dataset.action,v=b.dataset.value,live=currentBrew();
  if(dialog.open&&(['entry','method','reset'].includes(a)||dialog.contains(b)&&!['late-save','cold-late-save','timer-save','save-custom-timer','close-dialog'].includes(a)))closeModal();
  switch(a) {
    case 'entry':entry=v;navigateArea(v==='learn'?'learn':'brew');break;
    case 'method':chosen=v;if(area!=='brew'&&area!=='learn')area=entry==='learn'?'learn':'brew';sessionReturn=null;view=area==='learn'?'guide':'overview';browsing=false;render(false);break;
    case 'choose-set':chooseBrewingSet();break;
    case 'select-set':chosen=v;area='brew';entry='brew';sessionReturn=null;view='overview';browsing=false;render();break;
    case 'open-guide':chosen=v;area='learn';entry='learn';sessionReturn=null;view='guide';browsing=false;render();break;
    case 'compare-ui':compareCurrentUI=!compareCurrentUI;b.setAttribute('aria-pressed',String(compareCurrentUI));updateIntegrationContext();break;
    case 'retry-reference':updateIntegrationContext(true);break;
    case 'nav':navigateArea(v);break;
    case 'active-brews':openActiveBrews();break;
    case 'overview':if(sessionReturn||isLive()||view==='complete'||view==='cold-start')leaveSession();else{view='guide';browsing=false;render();}break;
    case 'back':if(sessionReturn)leaveSession();else if(!['brew','log','more'].includes(area)&&view==='overview')navigateArea('more');else{view=view==='guide'||isLive()||view==='cold-start'?'overview':'guide';browsing=false;render();}break;
    case 'allsteps':view='allsteps';browsing=!!live&&!live.complete;render();break;
    case 'explore':step=v===undefined?(Number.isInteger(reading[chosen])?Math.max(0,Math.min(reading[chosen],recipes[chosen].steps.length-1)):0):Number(v);view='step';browsing=true;render();break;
    case 'previous':step=Math.max(0,step-1);render();break;
    case 'next':step=Math.min(recipes[chosen].steps.length-1,step+1);render();break;
    case 'start':start();break;
    case 'start-guide':newBrew();break;
    case 'replace':newBrew();break;
    case 'return-session':openSession(v);break;
    case 'return-live':openSession(live.recipe);break;
    case 'start-clock':clockStart();break;
    case 'advance':advance();break;
    case 'early-stop':live.early=true;advance();break;
    case 'pause':markSampleEdited();live.paused=true;persist();render();break;
    case 'resume':markSampleEdited();live.paused=false;persist();render();break;
    case 'late':modal('Use your actual start time.','Enter the elapsed time since '+(chosen==='chemex'?'first water touched coffee':'the pump started')+'. Only enter a time you know. Preparation stays confirmed; no later physical step is marked done.','<label class="late-label" for="late-seconds">Elapsed seconds</label><input id="late-seconds" class="late-input" type="number" min="0" max="604800" step="1" inputmode="numeric" required placeholder="For example, 20"><p id="late-error" role="alert"></p>'+button('late-save','Use this start time')+button('unknown-time','I don’t know the time','secondary')+button('close-dialog','Go back','text-button'));break;
    case 'late-save':{const field=document.getElementById('late-seconds'),n=Number(field.value);if(field.value===''||!Number.isFinite(n)||n<0||n>604800||!Number.isInteger(n)){document.getElementById('late-error').textContent='Enter whole seconds from 0 to 604800.';field.focus();return;}closeModal();clockStart(n);break;}
    case 'cold-late':coldLate();break;
    case 'cold-late-save':{const field=document.getElementById('cold-start-time'),start=field.value?new Date(field.value).getTime():NaN;if(!Number.isFinite(start)||start>Date.now()){document.getElementById('late-error').textContent='Enter a known start time that is not in the future.';field.focus();return;}closeModal();clockStart((Date.now()-start)/1000);break;}
    case 'unknown-time':clockStart(null);break;
    case 'quick-timer':openQuickTimer();break;
    case 'replace-quick':markSampleEdited();sessions.cold=null;persist();view='cold-start';browsing=false;render();break;
    case 'quick-save':quickSave();break;
    case 'quick-unknown':quickSave(true);break;
    case 'edit-timer':modal('Change this timer',live.origin==null?'Choose the steep duration for this batch.':'The new duration uses the original start. Elapsed steeping time stays counted.',durationFields(live)+button('timer-save','Save timer')+button('close-dialog','Go back','text-button'));break;
    case 'timer-save':{const seconds=readDuration();if(seconds===null)return;putSession(ColdTimer.revise(live,seconds,document.getElementById('timer-remind').checked));closeModal();persist();render();break;}
    case 'quick-filtered':if(ColdTimer.ready(live)){markSampleEdited();live.complete=true;live.stoppedAt=Date.now();view='complete';persist();render();}break;
    case 'filter-help':step=4;view='step';browsing=true;render();break;
    case 'enable-alerts':modal('Allow brew reminders?','Get timed step reminders while you’re away from the app.',button('allow-alerts','Allow')+button('close-dialog','Not now','secondary'));break;
    case 'allow-alerts':alertPermission='allowed';document.getElementById('alert-control').value='allowed';persist();render();break;
    case 'end':modal(chosen==='cold'?'End this timer?':'End this brew?',chosen==='cold'?'The reminder will be removed. Your coffee keeps steeping until you separate the grounds.':'This ends the simulated session without marking the recipe complete. It does not stop a machine or make coffee safe to handle.',button('end-confirmed',chosen==='cold'?'End timer':'End brew')+button('close-dialog','Go back','secondary'));break;
    case 'end-confirmed':putSession(null);view='overview';browsing=false;persist();render();break;
    case 'close-dialog':closeModal();updateClock();break;
    case 'return-app':setPreviewSurface('app');break;
    case 'notification-open':openNotification(v,b.dataset.session);break;
    case 'notification-dismiss':{const x=sessionFor(v),cue=x&&timerDescription(x).cue;if(cue&&cue.key===b.dataset.cue){BrewTimers.acknowledge(x,cue,'app');persist();updateNotificationSurface();}break;}
    case 'reminder-options':{const x=view==='timer'?sessions.foreground:live;modal('Step reminders','Timed steps can notify you while you’re away. Elapsed-only stages keep a quiet progress notification.','<label class="reminder-check"><span>Timed step reminders</span><span class="switch-control"><input type="checkbox" role="switch" id="step-remind" '+(x.remind===false?'':'checked')+'><span class="switch-track" aria-hidden="true"></span></span></label>'+(alertPermission==='blocked'?'<p class="note">Notifications are off. Timers still run in the app.</p>'+button('enable-alerts','Enable notifications','secondary'):'')+button('save-step-reminder','Save')+button('close-dialog','Go back','text-button'));break;}
    case 'save-step-reminder':{const x=view==='timer'?sessions.foreground:live;markSampleEdited();x.remind=document.getElementById('step-remind').checked;x.alertRevision=(x.alertRevision||1)+1;persist();render();break;}
    case 'edit-custom-timer':modal('Change timer target','Use the time from your own recipe. This keeps the original start.','<label class="late-label" for="custom-seconds">Seconds</label><input id="custom-seconds" class="late-input" type="number" min="1" max="604800" step="1" value="'+sessions.foreground.customSeconds+'"><p id="timer-error" role="alert"></p>'+button('save-custom-timer','Save target')+button('close-dialog','Go back','text-button'));break;
    case 'save-custom-timer':{const n=Number(document.getElementById('custom-seconds').value);if(!Number.isInteger(n)||n<1||n>604800){document.getElementById('timer-error').textContent='Enter whole seconds from 1 to 604800.';return;}markSampleEdited();sessions.foreground.customSeconds=n;sessions.foreground.alertRevision=(sessions.foreground.alertRevision||1)+1;closeModal();persist();render();break;}
    case 'end-custom-timer':markSampleEdited();sessions.foreground=null;appAlert=null;persist();leaveSession();break;
    case 'dismiss-app-alert':appAlert=null;updateAppChrome();break;
    case 'time':markSampleEdited();[sessions.foreground,sessions.cold].filter(x=>x?.origin!=null&&!x.stoppedAt&&!x.complete).forEach(x=>{x.offset=(x.offset||0)+Number(v)*1000;});persist();updateClock();break;
    case 'large-text':{const enabled=app.classList.toggle('review-large-text');dialog.classList.toggle('review-large-text',enabled);document.getElementById('outside-app').classList.toggle('review-large-text',enabled);b.setAttribute('aria-pressed',String(enabled));break;}
    case 'reset':markSampleEdited();sessions={foreground:null,cold:null};appAlert=null;sessionReturn=null;view='overview';browsing=false;persist();render(false);break;
  }
});
document.getElementById('timer-method-control').innerHTML=Object.entries(BrewTimers.methods).map(([key,name])=>'<option value="'+key+'">'+name+'</option>').join('');
document.getElementById('timer-method-control').value='french-press';
document.getElementById('timer-method-control').addEventListener('change',()=>{if(!savedReview)setReviewState('method-timer');else{reviewState='method-timer';sessions=sampleBrews('method-timer');appAlert=null;sessionReturn=null;view='overview';browsing=false;document.getElementById('review-state').value=reviewState;render(false);}});
document.getElementById('surface-control').addEventListener('change',e=>setPreviewSurface(e.target.value));
render(false);setInterval(updateClock,1000);
