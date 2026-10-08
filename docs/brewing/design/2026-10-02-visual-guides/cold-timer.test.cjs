'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const timer = require('./cold-timer.js');
const brewTimers = require('./brew-timers.js');
const tests=[];
function test(name,fn){fn();tests.push(name);}
const origin=Date.UTC(2026,9,2,18,0);
const batch={recipe:'cold',index:3,origin,offset:0,stoppedAt:null,paused:false,complete:false,durationSeconds:14*3600,remind:true,alertRevision:1,alertedRevision:0,inAppAlertedRevision:0};
const lengths={chemex:9,espresso:6,cold:7};
test('accepts custom hours/minutes and rejects empty, fractional, zero and out-of-bounds input',()=>{
  assert.equal(timer.duration('13','30'),48600);
  for(const pair of [['','0'],['0','0'],['1.5','0'],['-1','0'],['1','60'],['168','1']]) assert.equal(timer.duration(...pair),null);
  assert.equal(timer.duration('0','1'),60);assert.equal(timer.duration('168','0'),604800);
});
test('deadline, remaining time and readiness share the persisted physical origin',()=>{
  assert.equal(timer.deadline(batch),origin+14*3600000);
  assert.equal(timer.remaining(batch,origin+3600000),13*3600);
  assert.equal(timer.ready(batch,origin+14*3600000-1),false);
  assert.equal(timer.ready(batch,origin+14*3600000),true);
});
test('duration edits retain elapsed steeping and invalidate an old alert revision',()=>{
  const changed=timer.revise({...batch,alertedRevision:1},16*3600);
  assert.equal(changed.origin,origin);assert.equal(changed.alertRevision,2);
  assert.equal(timer.previewAlert(changed,'allowed',origin+14*3600000),false);
  assert.equal(timer.previewAlert(changed,'allowed',origin+16*3600000),true);
  const shorter=timer.revise(batch,2*3600);assert.equal(timer.ready(shorter,origin+3*3600000),true);
  const unchanged=timer.revise({...batch,alertedRevision:1},batch.durationSeconds);
  assert.equal(unchanged.alertRevision,1);assert.equal(timer.previewAlert(unchanged,'allowed',origin+20*3600000),false);
});
test('unknown start cannot yield a deadline, ready state or reminder',()=>{
  const unknown={...batch,unknown:true,origin:null};
  assert.equal(timer.deadline(unknown),null);assert.equal(timer.remaining(unknown,origin),null);assert.equal(timer.ready(unknown,origin),false);assert.equal(timer.previewAlert(unknown,'allowed',origin),false);
});
test('alert preview respects denial, opt-out, deduplication, filtering and completion',()=>{
  const now=origin+20*3600000;
  assert.equal(timer.previewAlert(batch,'allowed',now),true);
  for(const x of [{...batch,remind:false},{...batch,complete:true},{...batch,index:4},{...batch,stoppedAt:now},{...batch,alertedRevision:1}])assert.equal(timer.previewAlert(x,'allowed',now),false);
  assert.equal(timer.previewAlert(batch,'blocked',now),false);
});
test('reload restores a cold timer alongside an unrelated foreground brew',()=>{
  const foreground={recipe:'espresso',index:4,origin:origin+1000,offset:0,stoppedAt:null};
  const saved=JSON.parse(JSON.stringify({version:2,cold:batch,foreground}));
  const restored=timer.restore(saved,lengths);
  assert.deepEqual(restored,{cold:batch,foreground});
  assert.equal(timer.remaining(restored.cold,origin+4*3600000),10*3600);
});
test('migration preserves a previous cold-brew guide without changing its 14-hour recipe',()=>{
  const old={recipe:'cold',index:3,origin,offset:0};
  const restored=timer.restore(old,lengths);
  assert.equal(restored.cold.durationSeconds,14*3600);assert.equal(restored.foreground,null);
  assert.equal(timer.restore({...old,paused:true},lengths).cold.paused,false);
  assert.equal(timer.restore({recipe:'chemex',index:9},lengths).foreground,null);
  assert.equal(timer.restore({...batch,durationSeconds:-1},lengths).cold,null);
  assert.equal(timer.restore({...batch,origin:'unknown'},lengths).cold,null);
  assert.equal(timer.restore({...batch,remind:'false'},lengths).cold,null);
  assert.equal(timer.restore({recipe:'cold',index:3},lengths).cold.unknown,true);
});
test('review time advancement remains stable across reload',()=>{
  const simulated={...batch,offset:14*3600000};
  const restored=timer.restore(JSON.parse(JSON.stringify({version:2,cold:simulated})),lengths);
  assert.equal(timer.deadline(restored.cold),origin);assert.equal(timer.ready(restored.cold,origin),true);
});

// Exercise actual prototype state transitions with a deterministic clock/storage.
// DOM rendering is inert here; browser review covers visible interaction/layout.
function harness(seed={}) {
  const storage=new Map(Object.entries(seed)), nodes=new Map(), handlers={};
  const node=id=>{if(!nodes.has(id))nodes.set(id,{value:'',checked:true,hidden:true,open:false,dataset:{},listeners:{},textContent:'',innerHTML:'',classList:{toggle:()=>false},addEventListener(type,fn){this.listeners[type]=fn;},setAttribute(){},focus(){},scrollIntoView(){},contains:el=>!!el?.insideDialog,querySelector:()=>null,show(){this.open=true;},close(){this.open=false;}});return nodes.get(id);};
  const context={URLSearchParams,Intl,Date:class extends Date{static now(){return origin;}},console,setInterval(){},location:{search:'?method=cold&entry=brew'},localStorage:{getItem:k=>storage.get(k)??null,setItem:(k,v)=>storage.set(k,v)},document:{getElementById:node,querySelectorAll:()=>[],documentElement:{dataset:{}},addEventListener:(k,fn)=>handlers[k]=fn}};
  node('app');node('dialog');
  vm.createContext(context);
  const html=fs.readFileSync(path.join(__dirname,'prototype.html'),'utf8');
  vm.runInContext(html.match(/<script>([\s\S]*?)<\/script>/)[1],context);
  vm.runInContext(fs.readFileSync(path.join(__dirname,'method-icons.js'),'utf8'),context);
  vm.runInContext(fs.readFileSync(path.join(__dirname,'cold-timer.js'),'utf8'),context);
  vm.runInContext(fs.readFileSync(path.join(__dirname,'brew-timers.js'),'utf8'),context);
  vm.runInContext(fs.readFileSync(path.join(__dirname,'app-shell.js'),'utf8'),context);
  vm.runInContext(fs.readFileSync(path.join(__dirname,'notification-surfaces.js'),'utf8'),context);
  vm.runInContext(fs.readFileSync(path.join(__dirname,'vessel-icons.js'),'utf8'),context);
  vm.runInContext(fs.readFileSync(path.join(__dirname,'current-ui.js'),'utf8'),context);
  vm.runInContext(fs.readFileSync(path.join(__dirname,'prototype.js'),'utf8'),context);
  return {run:code=>vm.runInContext(code,context),storage,node,click:(action,value,insideDialog=node('dialog').open)=>handlers.click({target:{closest:()=>({dataset:{action,value},insideDialog,disabled:false,setAttribute(){}})}})};
}
test('guided custom timer reaches filtering only after a manual confirmation',()=>{
  const h=harness();h.run('newBrew();advance();advance();putSession(ColdTimer.revise(currentBrew(),15*3600));clockStart();');
  assert.equal(h.run('sessions.cold.index'),3);
  h.click('time','50400');h.click('advance');assert.equal(h.run('sessions.cold.index'),3);
  h.click('time','3600');assert.equal(h.run('sessions.cold.index'),3);assert.equal(h.run('sessions.cold.complete'),false);
  h.click('advance');assert.equal(h.run('sessions.cold.index'),4);assert.equal(h.run('sessions.cold.complete'),false);
});
test('foreground brewing and reading cannot replace or advance a steeping batch',()=>{
  const h=harness();h.run('newBrew();advance();advance();clockStart();chosen="espresso";newBrew();');
  const savedCold=h.run('JSON.stringify(sessions.cold)');
  h.click('allsteps');h.click('explore','4');h.click('next');
  assert.equal(h.run('JSON.stringify(sessions.cold)'),savedCold);
  assert.equal(h.run('sessions.foreground.index'),0);
  h.click('return-session','cold');assert.equal(h.run('sessions.cold.index'),3);
  const reloaded=harness(Object.fromEntries(h.storage));assert.equal(reloaded.run('sessions.cold.index'),3);assert.equal(reloaded.run('sessions.foreground.recipe'),'espresso');
});
test('quick timer does not infer recipe confirmations, dose or brew completion',()=>{
  const h=harness();h.node('timer-hours').value='12';h.node('timer-minutes').value='30';h.node('timer-start').value='';h.run('quickSave();');
  assert.equal(h.run('sessions.cold.quick'),true);assert.equal(h.run('sessions.cold.durationSeconds'),45000);
  h.click('time','50400');assert.equal(h.run('sessions.cold.complete'),false);
  h.click('filter-help');assert.equal(h.run('browsing'),true);assert.equal(h.run('sessions.cold.index'),3);
  h.click('return-session','cold');h.click('quick-filtered');assert.equal(h.run('sessions.cold.complete'),true);
  assert.match(h.run('completed()'),/No recipe quantities or final yield were recorded/);
});
test('ending the cold timer preserves the foreground brew and stops alert previews',()=>{
  const h=harness();h.run('newBrew();advance();advance();clockStart();chosen="chemex";newBrew();chosen="cold";');h.click('end-confirmed');
  assert.equal(h.run('sessions.cold'),null);assert.equal(h.run('sessions.foreground.recipe'),'chemex');
  h.click('time','50400');assert.equal(h.node('outside-app').hidden,true);
});
test('short brew pause continues physical time, and unknown bloom cannot advance',()=>{
  const h=harness();h.run('chosen="chemex";newBrew();advance();advance();advance();clockStart(null);advance();');h.click('advance');assert.equal(h.run('sessions.foreground.index'),4);
  h.run('clockStart();');h.click('pause');h.click('time','45');assert.equal(h.run('elapsed()'),45);h.click('resume');h.click('advance');assert.equal(h.run('sessions.foreground.index'),5);
});
test('all 22 reader stages render with available artwork and reviewed source links',()=>{
  const h=harness();
  for(const recipe of ['chemex','espresso','cold']) {
    h.run('chosen='+JSON.stringify(recipe)+';view="step";browsing=true;');
    assert.ok(fs.existsSync(path.resolve(__dirname,h.run('recipes[chosen].source'))));
    for(let i=0;i<lengths[recipe];i++) {
      h.run('step='+i+';');const markup=h.run('stageScreen()');
      assert.match(markup,/class="instruction-image"/,'Each example step needs its instructional scene');
      assert.doesNotMatch(markup,/class="equipment-reference"/,'Do not substitute a recognition icon for a step scene');
      for(const match of markup.matchAll(/src="([^"]+)"/g))assert.ok(fs.existsSync(path.resolve(__dirname,match[1])),match[1]);
      assert.match(markup,/Explore freely/);assert.doesNotMatch(markup,/data-action="start-clock"/);
    }
  }
});
test('app navigation shares active brews without changing physical sessions or reader bookmarks',()=>{
  const h=harness();h.run('newBrew();advance();advance();clockStart();chosen="chemex";newBrew();');
  const before=h.run('JSON.stringify(sessions)');
  h.click('nav','learn');h.click('explore','4');
  assert.equal(h.run('reading.chemex'),4);
  for(const area of ['log','more','settings','brew','learn']){
    h.click('nav',area);
    assert.equal(h.run('JSON.stringify(sessions)'),before);
    const markup=h.node('app').innerHTML;
    if(['brew','log','more'].includes(area))assert.match(markup,/aria-label="Main navigation"/);
    else assert.doesNotMatch(markup,/aria-label="Main navigation"/,'Keep native detail-route navigation behavior');
    assert.match(markup,/aria-label="Active brews"/);
    assert.doesNotMatch(markup.split('<div class="app-dock">')[0],/resume-banner|active-brew-strip/,'Guide content must not own active-brew chrome');
  }
  assert.equal(h.run('reading.chemex'),4);
});
test('opening another timer and switching sessions returns to the exact reader step',()=>{
  const h=harness();h.run('newBrew();advance();advance();clockStart();chosen="chemex";newBrew();');
  h.click('nav','learn');h.click('explore','4');
  const before=h.run('JSON.stringify(sessions)');
  h.click('active-brews');assert.equal(h.node('dialog').open,true);
  assert.match(h.node('dialog').innerHTML,/Open Chemex/);assert.match(h.node('dialog').innerHTML,/Open Cold brew/);
  h.click('return-session','cold');assert.equal(h.run('area'), 'brew');assert.equal(h.run('browsing'),false);
  h.click('return-session','chemex');h.click('back');
  assert.equal(h.run('area'),'learn');assert.equal(h.run('chosen'),'chemex');assert.equal(h.run('step'),4);assert.equal(h.run('browsing'),true);
  assert.equal(h.run('JSON.stringify(sessions)'),before);
});
test('blocked OS notifications still allow one app-level reminder and persistent readiness',()=>{
  const h=harness({'starlit-visual-guide-v2':JSON.stringify({version:2,cold:{...batch,offset:14*3600000},foreground:null}),'starlit-preview-alert-permission':'blocked'});
  assert.match(h.node('app-alert').innerHTML,/Cold brew is ready to filter/);
  assert.equal(h.node('outside-app').hidden,true);
  assert.equal(h.run('sessions.cold.index'),3);assert.equal(h.run('sessions.cold.complete'),false);
  h.click('dismiss-app-alert');h.click('nav','log');h.click('time','45');
  assert.equal(h.node('app-alert').innerHTML,'');assert.match(h.node('app').innerHTML,/Ready to filter/);
  const restored=harness(Object.fromEntries(h.storage));
  assert.equal(restored.node('app-alert').innerHTML,'');assert.equal(restored.run('sessions.cold.inAppAlertedRevision'),1);
});
test('reminder opt-out, unknown time and visible timer suppress duplicate app alerts',()=>{
  for(const cold of [{...batch,offset:14*3600000,remind:false},{...batch,origin:null,unknown:true}]){
    const h=harness({'starlit-visual-guide-v2':JSON.stringify({version:2,cold,foreground:null})});
    assert.equal(h.node('app-alert').innerHTML,'');
  }
  const h=harness();h.run('newBrew();advance();advance();clockStart();');h.click('time','50400');
  assert.equal(h.node('app-alert').innerHTML,'');h.click('nav','more');
  assert.equal(h.node('app-alert').innerHTML,'');assert.match(h.node('app').innerHTML,/Ready to filter/);
});
test('timer edits invalidate old app reminders and cancellation removes every app surface',()=>{
  const h=harness({'starlit-visual-guide-v2':JSON.stringify({version:2,cold:{...batch,offset:14*3600000},foreground:null}),'starlit-preview-alert-permission':'blocked'});
  h.click('return-session','cold');h.click('edit-timer');h.node('timer-hours').value='16';h.node('timer-minutes').value='0';h.click('timer-save');
  assert.equal(h.node('app-alert').innerHTML,'');assert.equal(h.run('sessions.cold.origin'),origin);
  h.click('nav','more');h.click('time','7200');assert.match(h.node('app-alert').innerHTML,/Cold brew is ready to filter/);
  assert.equal(h.run('sessions.cold.index'),3);assert.equal(h.run('sessions.cold.complete'),false);
  h.click('return-session','cold');h.click('end-confirmed');
  assert.equal(h.run('sessions.cold'),null);assert.equal(h.node('app-alert').innerHTML,'');assert.doesNotMatch(h.node('app').innerHTML,/active-brew-strip/);
});
test('deadline delivery preserves the current screen and opening another brew keeps the reminder',()=>{
  const h=harness();h.run('newBrew();advance();advance();clockStart();chosen="chemex";newBrew();');
  h.click('nav','learn');h.click('explore','8');
  const content=h.node('app').innerHTML;
  h.click('time','50400');
  assert.equal(h.node('app').innerHTML,content,'Deadline must not rebuild a reader or reset its scroll/focus');
  assert.equal(h.run('area'),'learn');assert.equal(h.run('step'),8);assert.equal(h.run('sessions.cold.index'),3);
  assert.match(h.node('app-alert').innerHTML,/Cold brew is ready to filter/);
  h.click('return-session','chemex');assert.match(h.node('app-alert').innerHTML,/Cold brew is ready to filter/);
});
test('existing calculator and More entries remain the app landing surfaces',()=>{
  const h=harness();
  h.click('entry','brew');assert.match(h.node('app').innerHTML,/Brew calculator/);assert.match(h.node('app').innerHTML,/data-action="start"[^>]*>Start/);
  assert.doesNotMatch(h.node('app').innerHTML,/Your next brew|Prepare to brew/);
  h.click('nav','more');
  for(const text of ['Visual, step-by-step brewing guides','Your Favorites','Replay your favorite brews','Your Beans','Freshness, stock, and what to brew next','Methods, grinder and preferences'])assert.ok(h.node('app').innerHTML.includes(text),text);
  for(const route of ['favorites','beans','settings','learn']){
    h.click('nav',route);assert.doesNotMatch(h.node('app').innerHTML,/aria-label="Main navigation"/);
    h.click('back');assert.equal(h.run('area'),'more');
  }
});
test('library entry and calculator method selection do not start or replace a session',()=>{
  const h=harness();h.run('newBrew();advance();advance();clockStart();');const saved=h.run('JSON.stringify(sessions)');
  h.click('nav','learn');assert.match(h.node('app').innerHTML,/V60 02/);assert.match(h.node('app').innerHTML,/Starlit refrigerated recipe/);
  h.click('open-guide','chemex');assert.equal(h.run('view'),'guide');assert.equal(h.run('entry'),'learn');
  h.click('explore','3');h.click('overview');assert.equal(h.run('view'),'guide');
  h.click('back');assert.equal(h.run('view'),'overview');assert.match(h.node('app').innerHTML,/V60 02/);
  h.click('nav','brew');h.click('choose-set');h.click('select-set','espresso');assert.match(h.node('app').innerHTML,/In cup, 36 grams/);assert.match(h.node('app').innerHTML,/Water in, — grams/);
  h.click('select-set','chemex');assert.match(h.node('app').innerHTML,/In cup, — grams/);
  assert.equal(h.run('JSON.stringify(sessions)'),saved);
});
test('opening a timer from existing Settings restores its exact scroll and groups',()=>{
  const h=harness();h.run('newBrew();advance();advance();clockStart();');h.click('nav','settings');
  const before=h.run('JSON.stringify(sessions)');h.node('screen-content').scrollTop=560;
  h.click('return-session','cold');h.node('screen-content').scrollTop=0;h.click('back');
  assert.equal(h.run('area'),'settings');assert.equal(h.node('screen-content').scrollTop,560);
  for(const text of ['Brewing sets','Cup presets','Appearance','Scanning','Rating reminder','Brew vibration','Support & privacy'])assert.ok(h.node('app').innerHTML.includes(text),text);
  assert.doesNotMatch(h.node('app').innerHTML,/>Brew reminders</);assert.equal(h.run('JSON.stringify(sessions)'),before);
});
test('current UI comparison does not mutate sessions, route or reader bookmark',()=>{
  const h=harness();h.run('newBrew();');h.click('nav','learn');h.click('explore','4');
  const before=h.run('JSON.stringify({sessions,area,chosen,entry,view,step,reading})');
  h.click('compare-ui');h.click('compare-ui');assert.equal(h.run('JSON.stringify({sessions,area,chosen,entry,view,step,reading})'),before);
  for(const route of ['brew','more','log','learn','settings','favorites','beans'])assert.ok(fs.existsSync(path.join(__dirname,'assets/current-ui/'+route+'.png')),route);
});
test('a failed UI reference can retry without changing the screen or active brews',()=>{
  const h=harness();h.run('newBrew();advance();advance();clockStart();');h.click('nav','more');h.click('compare-ui');
  const before=h.run('JSON.stringify({sessions,area,chosen,entry,view,step,reading})'),image=h.node('current-ui-image'),error=h.node('current-ui-error');
  assert.equal(image.src,'assets/current-ui/more.png');
  image.listeners.error();assert.equal(image.hidden,true);assert.equal(error.hidden,false);
  h.click('retry-reference');assert.match(image.src,/more\.png\?retry=1$/);assert.equal(image.hidden,false);assert.equal(error.hidden,true);
  image.listeners.load();assert.equal(image.hidden,false);assert.equal(error.hidden,true);
  h.run('updateIntegrationContext();');assert.match(image.src,/more\.png\?retry=1$/,'A rerender must retain the successful retried image');
  assert.equal(h.run('JSON.stringify({sessions,area,chosen,entry,view,step,reading})'),before);
  h.click('nav','learn');assert.equal(image.src,'assets/current-ui/learn.png');
});
test('app dialogs block only the simulated app and close without changing a brew',()=>{
  const h=harness();h.run('chosen="chemex";newBrew();');const before=h.run('JSON.stringify(sessions)');
  h.click('choose-set');assert.equal(h.node('dialog').open,true);assert.equal(h.node('app-modal-layer').hidden,false);assert.equal(h.node('app').inert,true);
  h.click('compare-ui',undefined,false);assert.equal(h.node('dialog').open,true,'The external reference toggle must not dismiss an app dialog');
  h.click('large-text',undefined,false);assert.equal(h.node('dialog').open,true,'External text-size review must remain usable');
  h.click('close-dialog');assert.equal(h.node('dialog').open,false);assert.equal(h.node('app-modal-layer').hidden,true);assert.equal(h.node('app').inert,false);
  assert.equal(h.run('JSON.stringify(sessions)'),before);
  h.click('choose-set');h.click('select-set','espresso');assert.equal(h.node('app-modal-layer').hidden,true);assert.equal(h.node('app').inert,false);assert.equal(h.run('JSON.stringify(sessions)'),before);
});
test('review states show no timers, steeping, readiness and two brews without confirming filtration',()=>{
  const h=harness();
  h.run('setReviewState("none")');assert.equal(h.run('activeBrews().length'),0);assert.match(h.run('globalBrews()'),/hidden/);assert.equal(h.node('app-alert').innerHTML,'');
  h.run('setReviewState("steeping")');assert.equal(h.run('activeBrews().length'),1);assert.equal(h.run('ColdTimer.remaining(sessions.cold)'),12*3600);assert.equal(h.run('ColdTimer.ready(sessions.cold)'),false);
  h.run('setReviewState("ready")');assert.equal(h.run('ColdTimer.ready(sessions.cold)'),true);assert.equal(h.run('sessions.cold.index'),3);assert.match(h.run('globalBrews()'),/Ready to filter/);assert.match(h.node('app-alert').innerHTML,/Cold brew is ready/);
  h.run('setReviewState("multiple")');assert.equal(h.run('activeBrews().length'),2);assert.match(h.run('globalBrews()'),/2 in progress/);assert.equal(h.node('app-alert').innerHTML,'');assert.equal(h.node('announcement').textContent,'');
});
test('sample interactions and reload cannot overwrite saved brews, reading position or permissions',()=>{
  const h=harness({'starlit-visual-guide-v2':JSON.stringify({version:2,cold:batch,foreground:null}),'starlit-preview-alert-permission':'blocked'});
  h.click('nav','learn');h.click('explore','2');h.node('screen-content').scrollTop=237;
  const before=h.run('JSON.stringify({sessions,reading,alertPermission,area,chosen,entry,view,step,browsing})'),stored=Object.fromEntries(h.storage);
  h.run('setReviewState("ready")');h.click('time','3600');h.click('explore','5');h.run('alertPermission="allowed";persist();');h.click('end-confirmed');
  assert.deepEqual(Object.fromEntries(h.storage),stored);
  const reloaded=harness(Object.fromEntries(h.storage));assert.equal(reloaded.run('reviewState'),'saved');assert.equal(reloaded.run('sessions.cold.origin'),batch.origin);assert.equal(reloaded.run('sessions.cold.offset'),0);assert.equal(reloaded.run('reading.cold'),2);assert.equal(reloaded.run('alertPermission'),'blocked');
  h.run('setReviewState("saved")');assert.equal(h.run('JSON.stringify({sessions,reading,alertPermission,area,chosen,entry,view,step,browsing})'),before);assert.equal(h.node('screen-content').scrollTop,237);
});
test('cold brew Start offers guide or timer before creating a session and retains an existing batch',()=>{
  const h=harness();h.run('chosen="espresso";newBrew();chosen="cold";view="overview";');const foreground=h.run('JSON.stringify(sessions.foreground)');
  assert.doesNotMatch(h.run('nativeCalculator()'),/Just set a cold brew timer/);
  h.click('start');assert.equal(h.node('dialog').open,true);assert.match(h.node('dialog').innerHTML,/Follow the guide/);assert.match(h.node('dialog').innerHTML,/Just set a timer/);assert.equal(h.run('sessions.cold'),null);
  h.click('quick-timer');assert.equal(h.run('view'),'cold-start');assert.equal(h.run('sessions.cold'),null);assert.equal(h.run('JSON.stringify(sessions.foreground)'),foreground);
  h.click('overview');h.click('start');h.click('start-guide');assert.equal(h.run('sessions.cold.index'),0);assert.equal(h.run('sessions.cold.origin'),null);assert.equal(h.run('JSON.stringify(sessions.foreground)'),foreground);
  const cold=h.run('JSON.stringify(sessions.cold)');h.click('start');assert.match(h.node('dialog').innerHTML,/in progress/);h.click('close-dialog');assert.equal(h.run('JSON.stringify(sessions.cold)'),cold);
});
test('interacting with a sample changes its label and allows selecting the original fixture again',()=>{
  const h=harness();h.run('setReviewState("ready")');assert.equal(h.run('reviewState'),'ready','Automatic reminder delivery keeps the selected fixture');
  h.run('setReviewState("none")');h.click('start');h.click('quick-timer');h.node('timer-hours').value='13';h.node('timer-minutes').value='30';h.click('quick-save');
  assert.equal(h.run('reviewState'),'custom');assert.equal(h.node('review-state').value,'custom');assert.equal(h.run('sessions.cold.durationSeconds'),13.5*3600);
  h.run('setReviewState("none")');assert.equal(h.run('activeBrews().length'),0);assert.equal(h.run('reviewState'),'none');
});
test('all 17 methods share custom clock, deadline, cue and manual completion semantics',()=>{
  assert.equal(Object.keys(brewTimers.methods).length,17);
  for(const method of Object.keys(brewTimers.methods)){
    const live={recipe:'timer',method,index:0,origin,customSeconds:60,offset:0,remind:true};
    assert.equal(brewTimers.validCustom(live),true);
    assert.equal(brewTimers.describe(live,null,origin+20000).remaining,40);
    const due=brewTimers.describe(live,null,origin+60000);
    assert.equal(due.cue.title,brewTimers.methods[method]+' · Timer target reached');
    assert.equal(live.complete,undefined,'A time target never completes a brew');
    const unknown=brewTimers.describe({...live,unknown:true},null,origin+90000);
    assert.equal(unknown.cue,null);assert.equal(unknown.remaining,null);
  }
  const press=brewTimers.describe({recipe:'aeropress',index:0,origin,offset:0},{name:'AeroPress',steps:[{short:'Steep',wait:60}]},origin+60000);
  assert.equal(press.stage,'Step time reached');assert.doesNotMatch(press.cue.body,/pour/);
  const quiet=brewTimers.describe({recipe:'timer',method:'v60',index:0,origin,customSeconds:60,remind:false},null,origin+60000);
  assert.equal(quiet.ready,true);assert.equal(quiet.cue,null);
});
test('Chemex background bloom reminder is one event and never advances a physical step',()=>{
  const h=harness();h.run('setReviewState("bloom");setPreviewSurface("notifications");');
  assert.match(h.node('outside-app').innerHTML,/Chemex · Bloom/);assert.match(h.node('outside-app').innerHTML,/0:25 left/);
  h.click('time','45');assert.equal(h.run('sessions.foreground.index'),4);
  assert.match(h.node('outside-app').innerHTML,/Chemex · Bloom time reached/);assert.match(h.node('outside-app').innerHTML,/Reminder/);
  assert.equal(h.run('sessions.foreground.notifiedCues.length'),1);h.run('updateClock();updateClock();');assert.equal(h.run('sessions.foreground.notifiedCues.length'),1);
  assert.equal(h.node('app-alert').innerHTML,'','Do not send app banners while away');
});
test('espresso notification stays elapsed and yield-led without a made-up completion deadline',()=>{
  const h=harness();h.run('setReviewState("shot");setPreviewSurface("lock");');h.click('time','45');
  assert.match(h.node('outside-app').innerHTML,/Espresso/);assert.match(h.node('outside-app').innerHTML,/1:05/);
  assert.doesNotMatch(h.node('outside-app').innerHTML,/Ready|Timer target reached|notification-cue/);
  h.run('setPreviewSurface("notifications");');assert.match(h.node('outside-app').innerHTML,/Stop at 36 g in cup/);
  assert.equal(h.run('sessions.foreground.stoppedAt'),null);assert.equal(h.run('sessions.foreground.index'),4);
});
test('notification return opens the emitting stage and Back restores the prior reader and scroll',()=>{
  const h=harness();h.run('setReviewState("bloom");');h.click('method','chemex');h.click('nav','learn');h.click('explore','8');h.node('screen-content').scrollTop=419;
  const identity=h.run('BrewTimers.identity(sessions.foreground)');h.run('setPreviewSurface("notifications");');h.click('time','45');
  h.run('openNotification("chemex",'+JSON.stringify(identity)+');');
  assert.equal(h.run('previewSurface'),'app');assert.equal(h.run('isLive()'),true);assert.equal(h.run('sessions.foreground.index'),4);assert.equal(h.node('app-alert').innerHTML,'');
  h.click('back');assert.equal(h.run('area'),'learn');assert.equal(h.run('step'),8);assert.equal(h.node('screen-content').scrollTop,419);
});
test('blocked notifications hide every method notification while clocks and in-app cues keep working',()=>{
  const h=harness();h.run('setReviewState("bloom");alertPermission="blocked";setPreviewSurface("notifications");');h.click('time','45');
  assert.match(h.node('outside-app').innerHTML,/Notifications are off/);assert.doesNotMatch(h.node('outside-app').innerHTML,/os-notification /);
  assert.equal(h.run('sessions.foreground.notifiedCues'),undefined);assert.equal(h.run('elapsed(sessions.foreground)'),65);
  h.click('return-app');assert.match(h.node('app-alert').innerHTML,/Bloom time reached/);
});
test('any-method samples use the shared dock on every route and open their own contextual timer',()=>{
  const h=harness();h.run('setReviewState("method-timer");');
  for(const route of ['brew','log','more','learn','settings','beans','favorites']){
    h.click('nav',route);assert.match(h.run('globalBrews()'),/French press/);
    h.click('return-session','timer');assert.equal(h.run('view'),'timer');assert.match(h.node('app').innerHTML,/Target 1:00/);
    h.click('back');assert.equal(h.run('area'),route);
  }
});
test('custom target edits invalidate old notifications, retain origin and cancel only that timer',()=>{
  const h=harness();h.run('setReviewState("method-timer");setPreviewSurface("notifications");');h.click('time','45');
  assert.match(h.node('outside-app').innerHTML,/Timer target reached/);
  h.click('return-app');h.click('return-session','timer');h.click('edit-custom-timer');
  h.node('custom-seconds').value='120';h.click('save-custom-timer');assert.equal(h.node('dialog').open,false);assert.equal(h.run('sessions.foreground.origin'),origin-20000);
  h.run('setPreviewSurface("notifications");');assert.doesNotMatch(h.node('outside-app').innerHTML,/notification-cue/);assert.match(h.node('outside-app').innerHTML,/0:55 left/);
  h.click('return-app');h.click('end-custom-timer');h.run('setPreviewSurface("notifications");');assert.match(h.node('outside-app').innerHTML,/No active timers/);
});
test('dismissed or stale notifications cannot complete or open a replacement brew',()=>{
  const h=harness();h.run('setReviewState("bloom");setPreviewSurface("notifications");');h.click('time','45');
  h.run('BrewTimers.acknowledge(sessions.foreground,timerDescription(sessions.foreground).cue,"app");updateNotificationSurface();');
  assert.doesNotMatch(h.node('outside-app').innerHTML,/notification-cue/);assert.equal(h.run('sessions.foreground.complete'),false);
  h.run('openNotification("chemex","stale-session");');assert.equal(h.run('previewSurface'),'notifications');assert.match(h.node('announcement').textContent,/has ended/);
});
test('custom clocks and reminder acknowledgement restore after reload without resetting time',()=>{
  const live={recipe:'timer',method:'aeropress',index:0,origin:origin-20000,offset:45000,customSeconds:60,stoppedAt:null,complete:false,remind:true};
  const d=brewTimers.describe(live,null,origin);brewTimers.acknowledge(live,d.cue,'os');
  const h=harness({'starlit-visual-guide-v2':JSON.stringify({version:2,foreground:live,cold:batch})});
  assert.equal(h.run('timerDescription(sessions.foreground).seconds'),65);assert.equal(h.run('sessions.cold.origin'),batch.origin);
  h.run('setPreviewSurface("notifications");');assert.equal(h.run('sessions.foreground.notifiedCues.length'),1);
  const invalid=harness({'starlit-visual-guide-v2':JSON.stringify({version:2,foreground:{...live,method:'unknown'},cold:batch})});assert.equal(invalid.run('sessions.foreground'),null);
});
test('paused guidance and unknown starts do not manufacture background step reminders',()=>{
  const h=harness();h.run('setReviewState("bloom");sessions.foreground.paused=true;setPreviewSurface("notifications");');h.click('time','45');
  assert.equal(h.run('sessions.foreground.notifiedCues'),undefined);assert.equal(h.run('elapsed(sessions.foreground)'),65);
  h.run('sessions.foreground.unknown=true;updateClock();');assert.match(h.node('outside-app').innerHTML,/Time unknown/);assert.doesNotMatch(h.node('outside-app').innerHTML,/notification-cue/);
});
console.log('PASS: '+tests.length+' cold-timer and app-wide prototype transition checks');
for(const name of tests) console.log('  '+name);
