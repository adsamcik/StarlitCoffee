/* Shared app chrome. Sessions belong to the app; a reader only owns its bookmark. */
'use strict';
let sessionReturn = null, appAlert = null;
const appAreas = ['brew','learn','log','more','settings','favorites','beans'];
const navIcons = {
  log:'<path d="M13 3a9 9 0 0 0-9 9H1l4 4 4-4H6a7 7 0 1 1 2 4.9l-1.4 1.4A9 9 0 1 0 13 3Zm-1 5v5l4 2 .9-1.7-2.9-1.5V8Z"/>',
  more:'<circle cx="5" cy="12" r="2"/><circle cx="12" cy="12" r="2"/><circle cx="19" cy="12" r="2"/>',
  close:'<path d="m18.3 5.7-1.4-1.4L12 9.2 7.1 4.3 5.7 5.7l4.9 4.9-4.9 4.9 1.4 1.4 4.9-4.9 4.9 4.9 1.4-1.4-4.9-4.9 4.9-4.9Z"/>',
  learn:'<path d="M16 1H9v15l3.5-3 3.5 3V1ZM5 3H3v17h7c1.6 0 3 .6 4 1.6 1-1 2.4-1.6 4-1.6h5V3h-2v15h-3c-1.5 0-2.9.4-4 1.1-1.1-.7-2.5-1.1-4-1.1H5V3Z"/>',
  settings:'<path d="m19.4 13.5 1.4 1.1-1.4 2.4-1.7-.6a8 8 0 0 1-2.6 1.5l-.3 1.8h-2.8l-.3-1.8a8 8 0 0 1-2.6-1.5l-1.7.6L6 14.6l1.4-1.1a8 8 0 0 1 0-3L6 9.4 7.4 7l1.7.6a8 8 0 0 1 2.6-1.5l.3-1.8h2.8l.3 1.8a8 8 0 0 1 2.6 1.5l1.7-.6 1.4 2.4-1.4 1.1a8 8 0 0 1 0 3ZM13.4 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6Z"/>',
  favorites:'<path d="M6 3h12v19l-6-3-6 3V3Z"/>',
  beans:'<path d="M17 6h-1a4 4 0 0 0-8 0H7a3 3 0 0 0-3 3v12h16V9a3 3 0 0 0-3-3ZM10 6a2 2 0 0 1 4 0h-4Zm-1 6a1 1 0 0 1-1-1V8h2v3a1 1 0 0 1-1 1Zm6 0a1 1 0 0 1-1-1V8h2v3a1 1 0 0 1-1 1Z"/>',
  save:'<path d="M5 3h12v4h4v3h-4v4h-3v-4h-4V7h4V3H5v19l6-3 6 3v-6h-3l-3-1.5L8 16V6H5V3Z"/>',
  backspace:'<path d="M22 3H7L1 12l6 9h15V3Zm-4.3 12.3-1.4 1.4L13 13.4l-3.3 3.3-1.4-1.4 3.3-3.3-3.3-3.3 1.4-1.4 3.3 3.3 3.3-3.3 1.4 1.4-3.3 3.3 3.3 3.3Z"/>',
  star:'<path d="m12 2 3.1 6.3L22 9.3l-5 4.9 1.2 6.9-6.2-3.3-6.2 3.3L7 14.2 2 9.3l6.9-1L12 2Z"/>'
};
function navIcon(name) { return name==='brew' ? vesselIcon('cappuccino') : '<svg viewBox="0 0 24 24" aria-hidden="true" fill="currentColor">'+navIcons[name]+'</svg>'; }
function activeBrews() { return [sessions.foreground,sessions.cold].filter(x=>x&&!x.complete); }
function sessionState(live) {
  const d=timerDescription(live);
  return {ready:d.ready,title:d.name+' · '+d.stage,detail:timerStatus(live)};
}
function activitySession() {
  const active=activeBrews(),other=active.filter(x=>!(isLive()&&currentBrew()===x||view==='timer'&&x.recipe==='timer'));
  return other.find(x=>sessionState(x).ready)||other.find(x=>x.recipe!=='cold')||other[0]||null;
}
function compactSessionDetail(live) {
  const state=sessionState(live),d=timerDescription(live);
  if(state.ready)return d.stage;
  if(live.recipe==='timer')return timerStatus(live);
  if(d.remaining!==null&&live.recipe!=='cold')return d.stage+' · '+time(d.remaining)+' left';
  if(live.recipe==='cold'&&live.index===3)return remainingLabel(live);
  if(live.origin==null&&!live.unknown)return 'Preparing';
  return (live.paused?'Guide paused':recipes[live.recipe].steps[live.index].short)+' · '+clockText(live);
}
function globalBrews() {
  const live=activitySession(),active=activeBrews();
  if(!live)return '<section id="global-brews" aria-label="Active brews" hidden></section>';
  const state=sessionState(live),multiple=active.length>1;
  return '<section id="global-brews" aria-label="Active brews"><button class="active-brew-strip '+(state.ready?'is-ready':'')+'" data-action="'+(multiple?'active-brews':'return-session')+'" data-value="'+live.recipe+'" aria-label="'+(multiple?'Open active brews, '+active.length+' in progress':'Open '+timerName(live))+'"><span class="activity-symbol">'+timerIcon(live)+'</span><span class="activity-copy"><b data-activity-title>'+esc(timerName(live))+'</b><span data-activity-detail>'+esc(compactSessionDetail(live))+'</span></span>'+(multiple?'<span class="activity-count" aria-hidden="true">'+active.length+'</span>':'')+icons.chevron+'</button></section>';
}
function activeBrewList() {
  return '<div class="active-brew-list">'+activeBrews().sort((a,b)=>Number(sessionState(b).ready)-Number(sessionState(a).ready)).map(live=>{const state=sessionState(live);return button('return-session','<span class="setting-icon">'+timerIcon(live)+'</span><span class="activity-copy"><b>'+esc(timerName(live))+'</b><span data-session-detail="'+live.recipe+'">'+esc(state.ready?timerDescription(live).stage:state.detail)+'</span></span>'+icons.chevron,'session-row','data-value="'+live.recipe+'" aria-label="Open '+timerName(live)+'"');}).join('')+'</div>';
}
function openActiveBrews() { modal('Active brews','',activeBrewList()+button('close-dialog','Close','text-button'));dialog.dataset.kind='active-brews'; }
function openSession(recipe) {
  const live=sessionFor(recipe);if(!live)return;
  if(!sessionReturn&&(!isLive()||chosen!==recipe))sessionReturn={area,chosen,entry,view,step,browsing,scrollTop:document.getElementById('screen-content').scrollTop||0};
  if(recipe!=='timer')chosen=recipe;area='brew';entry='brew';view=recipe==='timer'?'timer':live.complete?'complete':'step';browsing=false;
  if(appAlert?.recipe===recipe)appAlert=null;render();
}
function leaveSession() {
  if(sessionReturn){const previous=sessionReturn;({area,chosen,entry,view,step,browsing}=previous);sessionReturn=null;render();document.getElementById('screen-content').scrollTop=previous.scrollTop;return;}
  view='overview';browsing=false;render();
}
function navigateArea(next) {
  if(!appAreas.includes(next))return;
  rememberReading();area=next;sessionReturn=null;view='overview';browsing=false;
  if(next==='learn')entry='learn';else if(next==='brew')entry='brew';
  render();
}
function appHeader() {
  if(area==='brew'&&view==='overview')return '';
  const guide=(area==='learn'||area==='brew')&&view!=='overview';
  const title=guide?(view==='timer'?timerName(sessions.foreground):view==='cold-start'?'Cold brew timer':recipes[chosen].name):nativeTitles[area];
  const back=guide||!['brew','log','more'].includes(area);
  const extra=area==='learn'&&view==='overview'?' native-library-top':area==='settings'?' native-settings-top':' native-screen-top';
  return '<header class="app-top'+extra+'">'+(back?button('back',icons.back,'icon-button','aria-label="'+(sessionReturn?'Return to previous screen':!guide?'Back to More':'Back')+'"'):'')+(area==='settings'?'':'<'+(guide||area==='learn'?'span':'h1')+' class="app-title" '+(guide||area==='learn'?'':'tabindex="-1"')+'>'+title+'</'+(guide||area==='learn'?'span':'h1')+'>')+(guide&&view!=='timer'?button('allsteps','Steps','icon-button'):'')+'</header>';
}
function appNavigation() {
  // Match StarlitNavHost.bottomBarRoutes. Detail routes retain their Back action.
  if(!['brew','log','more'].includes(area)||area==='brew'&&view!=='overview')return '';
  const selected=area==='brew'?'brew':area==='log'?'log':'more';
  return '<nav class="app-navigation" aria-label="Main navigation">'+['brew','log','more'].map(name=>'<button data-action="nav" data-value="'+name+'" '+(selected===name?'aria-current="page"':'')+'><span class="nav-symbol">'+navIcon(name)+'</span><span>'+name[0].toUpperCase()+name.slice(1)+'</span></button>').join('')+'</nav>';
}
function otherAreaScreen() {
  if(area==='log')return nativeLog();
  if(area==='settings')return nativeSettings();
  if(area==='favorites'||area==='beans')return nativeEmpty(area);
  return nativeMore();
}
function appAlertMarkup() {
  const live=appAlert&&sessionFor(appAlert.recipe),cue=live&&timerDescription(live).cue;
  if(!cue||cue.key!==appAlert.key||isLive()&&currentBrew()===live||view==='timer'&&live.recipe==='timer')return '';
  return '<section class="app-ready-alert" aria-label="Brew reminder"><span class="alert-copy"><b>'+esc(cue.title)+'</b></span>'+button('return-session','Open','text-button','data-value="'+live.recipe+'"')+button('dismiss-app-alert',navIcon('close'),'icon-button','aria-label="Dismiss brew reminder"')+'</section>';
}
function updateAppChrome() {
  const live=activitySession(),state=live?sessionState(live):null;
  document.querySelectorAll('[data-activity-title]').forEach(el=>el.textContent=live?timerName(live):'');
  document.querySelectorAll('[data-activity-detail]').forEach(el=>el.textContent=live?compactSessionDetail(live):'');
  document.querySelectorAll('.active-brew-strip').forEach(el=>el.classList.toggle('is-ready',!!state?.ready));
  document.querySelectorAll('[data-session-detail]').forEach(el=>{const live=sessionFor(el.dataset.sessionDetail);if(live){const state=sessionState(live);el.textContent=state.ready?timerDescription(live).stage:state.detail;}});
  const region=document.getElementById('app-alert'),markup=appAlertMarkup();
  if(region.innerHTML!==markup)region.innerHTML=markup;
}
