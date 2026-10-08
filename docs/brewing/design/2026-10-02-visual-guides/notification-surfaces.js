/* Review-only Android surfaces. This file never requests or sends OS notifications. */
'use strict';
let previewSurface='app', notificationReturn=null;
function timerDescription(live) { return BrewTimers.describe(live,recipes[live.recipe]); }
function timerName(live) { return BrewTimers.name(live,recipes[live.recipe]); }
function timerIcon(live) { return equipmentIcon(live.recipe==='timer'?live.method:live.recipe); }
function notificationClock(live) { const d=timerDescription(live);return d.ready?clockText(live)+' · '+d.clockName:timerStatus(live); }
function timerStatus(live) {
  const d=timerDescription(live);
  return d.seconds===null?(live.unknown?'Time unknown':'Preparing'):d.ready?d.stage:d.remaining!==null?time(d.remaining)+' left':clockText(live)+' · '+d.clockName;
}
function setPreviewSurface(next) {
  if(!['app','notifications','lock'].includes(next))return;
  if(next===previewSurface)return;
  closeModal();
  if(previewSurface==='app'&&next!=='app')notificationReturn={scrollTop:document.getElementById('screen-content').scrollTop||0};
  previewSurface=next;document.getElementById('surface-control').value=next;
  app.classList.toggle('app-away',next!=='app');app.setAttribute('aria-hidden',String(next!=='app'));app.inert=next!=='app';
  document.getElementById('outside-app').hidden=next==='app';
  if(next==='app'){
    document.getElementById('screen-content').scrollTop=notificationReturn?.scrollTop||0;notificationReturn=null;
  }
  updateClock();updateIntegrationContext();
}
function openNotification(recipe,identity) {
  const live=sessionFor(recipe);
  if(!live||live.complete||BrewTimers.identity(live)!==identity){document.getElementById('announcement').textContent='This brew has ended. Your current brews are unchanged.';return;}
  const cue=timerDescription(live).cue;if(cue)BrewTimers.acknowledge(live,cue,'app');persist();
  setPreviewSurface('app');openSession(recipe);
}
function notificationCard(live,lock=false) {
  const d=timerDescription(live),cue=d.cue,acknowledged=cue&&BrewTimers.delivered(live,cue,'app');
  const alert=cue&&!acknowledged&&BrewTimers.delivered(live,cue,'os');
  const title=alert?cue.title:d.name+' · '+d.stage;
  return '<article class="os-notification '+(alert?'notification-cue':'')+'" aria-label="'+esc(title)+'"><div class="notification-source"><span>'+navIcon('brew')+' Starlit Coffee</span><span>'+(alert?'Reminder':'Ongoing brew')+'</span></div><div class="notification-heading"><h2>'+esc(title)+'</h2><span class="notification-method" aria-hidden="true">'+timerIcon(live)+'</span></div><p class="notification-time" data-notification-clock="'+live.recipe+'">'+esc(notificationClock(live))+'</p>'+(!lock?'<p class="notification-body">'+esc(alert?cue.body:d.hint||(!d.ready&&d.remaining!==null?'Target '+dateLabel(d.due):'Your place in the brew is saved.'))+'</p>':'')+'<div class="notification-actions">'+button('notification-open',alert?cue.action:'Return to brew','text-button','data-value="'+live.recipe+'" data-session="'+esc(BrewTimers.identity(live))+'"')+(alert?button('notification-dismiss','Dismiss','text-button','data-value="'+live.recipe+'" data-cue="'+esc(cue.key)+'"'):'')+'</div></article>';
}
function updateNotificationSurface() {
  const outside=document.getElementById('outside-app');if(previewSurface==='app')return;
  const lock=previewSurface==='lock';
  const running=activeBrews().filter(live=>Number.isFinite(live.origin)||live.unknown);
  const signature=JSON.stringify([previewSurface,alertPermission,running.map(live=>{const d=timerDescription(live);return [BrewTimers.identity(live),live.index,live.offset,live.unknown,live.paused,live.remind,live.alertRevision,d.ready,d.due,d.target,d.cue?.key,live.seenCues,live.notifiedCues];})]);
  if(outside.dataset.signature===signature){
    document.querySelectorAll('[data-notification-clock]').forEach(el=>{const live=sessionFor(el.dataset.notificationClock);if(live)el.textContent=notificationClock(live);});
    document.querySelectorAll('[data-lock-clock]').forEach(el=>el.textContent=new Intl.DateTimeFormat(undefined,{hour:'numeric',minute:'2-digit'}).format(new Date()));
    return;
  }
  const markup='<div class="os-preview '+(lock?'lock-preview':'')+'"><div class="os-preview-top"><span>ANDROID · '+(lock?'LOCK SCREEN':'NOTIFICATIONS')+'</span>'+button('return-app','Open app','text-button')+'</div>'+(lock?'<div class="lock-clock" data-lock-clock>'+new Intl.DateTimeFormat(undefined,{hour:'numeric',minute:'2-digit'}).format(new Date())+'</div>':'<h1>Notifications</h1>')+'<div class="os-notifications">'+(alertPermission==='blocked'?'<div class="os-empty"><span class="setting-icon">'+icons.bell+'</span><h2>Notifications are off</h2><p>Timers keep running. Open the app to check them or enable reminders.</p>'+button('return-app','Open app','secondary')+'</div>':running.length?running.sort((a,b)=>Number(timerDescription(b).ready)-Number(timerDescription(a).ready)).map(live=>notificationCard(live,lock)).join(''):'<div class="os-empty"><h2>No active timers</h2><p>Start a brew in the app to see its progress here.</p></div>')+'</div><p class="os-preview-note">Design simulation · Android controls the final appearance'+(lock?' and lock-screen privacy.':'.')+'</p></div>';
  outside.innerHTML=markup;outside.dataset.signature=signature;
}
function customTimerScreen() {
  const live=sessions.foreground,d=timerDescription(live);
  return '<div class="content cold-wait"><section class="steep-hero"><div class="steep-symbol" aria-hidden="true">'+timerIcon(live)+'</div><h1 tabindex="-1">'+esc(d.name)+'</h1><p data-custom-stage>'+esc(d.stage)+'</p><div class="expressive-time" data-custom-time>'+esc(d.remaining===null?'Time unknown':time(d.remaining))+'</div><p class="intro-sub">Target '+time(live.customSeconds)+'</p></section><p class="timer-guidance">Your chosen time. Confirm the next action with your recipe.</p><div class="timer-settings">'+button('edit-custom-timer','<span class="setting-icon">'+icons.schedule+'</span><span class="setting-label">Timer target</span><span class="setting-value">'+time(live.customSeconds)+'</span>'+icons.chevron,'setting-row')+button('reminder-options','<span class="setting-icon">'+icons.bell+'</span><span class="setting-label">Step reminder</span><span class="setting-value">'+reminderStatus(live)+'</span>'+icons.chevron,'setting-row')+'</div></div><footer class="bottom">'+button('overview','Done for now')+button('end-custom-timer','End timer','text-button')+'</footer>';
}
