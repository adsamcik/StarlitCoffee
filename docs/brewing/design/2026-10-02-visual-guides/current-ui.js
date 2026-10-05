/* Existing screen context, reconstructed from native source and emulator captures.
   This is a layout preview, not a second implementation of app preferences or math. */
'use strict';
let compareCurrentUI = false;
let referenceRetry = 0;
const nativeTitles = {brew:'Brew',log:'Brew Log',more:'More',learn:'Learn',settings:'Settings',favorites:'Your Favorites',beans:'Your Beans'};
const vessels = ['espresso','cortado','cappuccino','mug','travel'];
function staticControl(label,cls='',extra='') {
  return '<button class="native-static '+cls+'" disabled '+extra+'>'+label+'</button>';
}
function nativeCalculator() {
  const r=recipes[chosen],coffee=r.dose.split(' ')[0],water=chosen==='espresso'?'—':r.quantity.split(' ')[0];
  // Match native output semantics: no Chemex coefficient; espresso targets cup yield.
  const cup=chosen==='espresso'?'36':chosen==='cold'?'≈600':'—';
  const ratio=chosen==='espresso'?'1:2':chosen==='cold'?'1:8':'1:16';
  const quantities=[['coffee','Coffee',coffee],['water','Water in',water],['cup','In cup',cup]];
  return '<div class="native-calculator"><h1 class="sr-only" tabindex="-1">Brew calculator</h1><div class="calc-expression"><strong>'+coffee+'</strong>'+staticControl(navIcon('save'),'icon-button','aria-label="Save as set"')+'</div><div class="calc-preview">'+quantities.map(([key,label,value],i)=>staticControl(calculationIcon(key)+'<b>'+label+'</b><strong>'+value+(value==='—'?'':'<small> g</small>')+'</strong>','calc-quantity '+(i===0?'selected':''),'aria-label="'+label+', '+value+' grams"')).join('')+'</div><div class="calc-spacer"></div><div class="calc-controls"><div class="calc-pills">'+button('choose-set','<span class="calc-method-symbol">'+equipmentIcon(chosen)+'</span><span><b>'+r.name+'</b><small>'+(chosen==='chemex'?'Bonded paper':chosen==='espresso'?'Two-cup basket':'Refrigerated')+'</small></span>'+icons.chevron,'calc-method','aria-label="Choose brewing set"')+staticControl(ratio+' '+icons.chevron,'calc-ratio','aria-label="Brew ratio '+ratio+'"')+'</div><div class="calc-presets">'+vessels.map(v=>staticControl(vesselIcon(v),'', 'aria-label="'+v[0].toUpperCase()+v.slice(1)+' cup preset"')).join('')+staticControl(navIcon('backspace'),'calc-backspace','aria-label="Backspace"')+'</div><div class="calc-keypad">'+['7','8','9','×','4','5','6','+','1','2','3'].map(label=>staticControl(label,'calc-key')).join('')+button('start','Start','calc-key calc-start')+staticControl('0','calc-key calc-zero')+staticControl('.','calc-key')+staticControl('C','calc-key calc-clear')+'</div>'+'</div></div>';
}
function chooseBrewingSet() {
  modal('Brewing sets','', '<div class="native-set-list">'+Object.keys(recipes).map(key=>button('select-set','<span class="setting-icon">'+equipmentIcon(key)+'</span><span class="activity-copy"><b>'+recipes[key].name+'</b><span>'+recipes[key].subtitle+'</span></span>'+(chosen===key?icons.check:''),'session-row','data-value="'+key+'"')).join('')+'</div>'+button('nav','Manage sets','secondary','data-value="settings"')+button('close-dialog','Close','text-button'));
}
function nativeMore() {
  const items=[['learn','Learn','Visual, step-by-step brewing guides'],['favorites','Your Favorites','Replay your favorite brews'],['beans','Your Beans','Freshness, stock, and what to brew next'],['settings','Settings','Methods, grinder and preferences']];
  return '<div class="native-more"><div class="more-list">'+items.map(([route,title,detail])=>button('nav','<span class="setting-icon">'+navIcon(route)+'</span><span class="activity-copy"><b>'+title+'</b><span>'+detail+'</span></span>','more-row','data-value="'+route+'"')).join('')+'</div></div>';
}
function libraryCard(name,visual,rows,key) {
  return '<article class="native-library-card"><div class="native-library-heading"><span class="profile-badge">'+visual+'</span><h2>'+name+'</h2></div>'+rows.map(([title,detail])=>key?button('open-guide','<span class="recipe-accent"></span><span class="activity-copy"><b>'+title+'</b><span>'+detail+'</span></span><span class="recipe-arrow">'+icons.forward+'</span>','native-recipe-row','data-value="'+key+'"'):staticControl('<span class="recipe-accent"></span><span class="activity-copy"><b>'+title+'</b><span>'+detail+'</span></span><span class="recipe-arrow">'+icons.forward+'</span>','native-recipe-row')).join('')+'</article>';
}
function libraryGroup(label) { return '<h2 class="native-library-group">'+label+'<span></span></h2>'; }
function nativeLibrary() {
  const icon=name=>equipmentIcon(name);
  return '<div class="native-library"><h1 class="sr-only" tabindex="-1">Learn</h1>'+libraryGroup('Manual gravity')+libraryCard('V60 02',icon('v60_02'),[['Hario official','15 g / 250 g'],['Scott Rao V60','20 g / 330 g']])+libraryCard('Flat-bottom Wave 185',icon('wave_185'),[['Ozone Wave 185','25 g / 400 g']])+libraryCard('Wedge dripper',icon('wedge'),[['Wedge pulse','23.5 g / 400 g']])+libraryCard('Chemex',equipmentIcon('chemex'),[['Starlit starting recipe','30 g / 480 g']],'chemex')+libraryGroup('Espresso')+libraryCard('Bambino Plus',equipmentIcon('espresso'),[['One measured shot','18 g / 36 g in the cup']],'espresso')+libraryGroup('Cold brew')+libraryCard('Refrigerated cold brew',equipmentIcon('cold'),[['Starlit refrigerated recipe','100 g / 800 g · 14 h']],'cold')+'</div>';
}
function nativeLog() {
  // Existing emulator history is context; completing a preview timer does not write a log.
  return '<div class="native-log"><article class="native-log-card"><div class="native-log-heading"><span class="setting-icon">'+equipmentIcon('espresso')+'</span><span><h2>Espresso</h2><p>Aug 27, 2026 · 2:48 PM</p></span></div><span class="pill">18g · 306g · 1:17</span>'+staticControl(navIcon('star')+' Rate brew','native-rate')+'</article></div>';
}
function nativeEmpty(route) {
  const beans=route==='beans';
  return '<div class="native-empty"><div class="empty-symbol">'+navIcon(route)+'</div><h1 tabindex="-1">'+(beans?'No beans yet':'No favorites yet')+'</h1><p>'+(beans?'Scan the front and back to fill in the details automatically. Photos stay on your device.':'Save a recipe from the brew screen with the ♡ button')+'</p>'+(beans?staticControl('Scan my first bag','primary')+staticControl('Add manually','text-button'):'')+'</div>'+(beans?'<div class="native-fab">'+staticControl('+ Add coffee','secondary')+'</div>':'');
}
function settingContextRow(title,detail,kind='navigation',checked=false) {
  return '<div class="native-setting-row"><span><b>'+title+'</b>'+(detail?'<p>'+detail+'</p>':'')+'</span>'+(kind==='switch'?'<span class="native-switch '+(checked?'checked':'')+'" aria-hidden="true"></span>':icons.chevron)+'</div>';
}
function nativeSettings() {
  const group=(title,body)=>'<h2 class="native-settings-heading">'+title+'</h2><div class="native-settings-group">'+body+'</div>';
  const presets=[['Espresso','36'],['Cortado','130'],['Cappuccino','180'],['Mug','374'],['Travel','500']];
  return '<div class="native-settings"><h1 tabindex="-1">Settings</h1>'+group('Brewing','<h2>Brewing sets</h2><p>Keep separate setups for home, work, or different brewing methods.</p><div class="native-saved-set selected"><span class="setting-icon">'+equipmentIcon('pulsar')+'</span><span><b>Pulsar</b><p>Paper</p></span>'+icons.check+'</div><div class="native-saved-set"><span class="setting-icon">'+equipmentIcon('espresso')+'</span><b>Espresso</b></div>'+staticControl('+ Add set','secondary'))+'<div class="native-settings-group"><div class="native-settings-actions"><h2>Cup presets</h2>'+staticControl('+','icon-button','aria-label="Add preset"')+staticControl(navIcon('log'),'icon-button','aria-label="Reset to defaults"')+'</div><p>Presets appear as quick buttons on the calculator</p>'+presets.map(([title,amount],i)=>'<div class="native-setting-row"><span class="setting-icon">'+vesselIcon(vessels[i])+'</span><span><b>'+title+'</b><p>'+amount+' ml</p></span>'+icons.chevron+'</div>').join('')+'</div><div class="native-settings-group">'+settingContextRow('Quick brew','Skip the grind prep step and go straight to the timer when you start a brew','switch')+settingContextRow('Show brewing instructions','Show the step-by-step guidance card during brewing and the prep checklist on the prep screen. Turn off if you know your method by heart.','switch',true)+'</div>'+group('Appearance',settingContextRow('Display & dim mode','Dim mode, OLED black, brightness')+settingContextRow('Bloom animations','Choose how often each bloom sprite appears'))+group('Scanning',settingContextRow('Enhanced label recognition','Look for more bag details on this device when available. Basic on-device text reading from the label remains available.','switch',true)+settingContextRow('Label recognition and privacy',''))+group('Notifications',settingContextRow('Rating reminder','Send a notification ~30 minutes after a brew so you can rate it while the taste is fresh.','switch')+'<div class="native-vibration"><b>Brew vibration</b><p>Choose the feel of bloom and brew-time cues. Your phone’s notification settings can still silence them.</p><div class="segmented">'+['Soft','Classic','Bold'].map((label,i)=>staticControl(label,i===0?'selected':'')).join('')+'</div></div>')+group('Support & privacy',settingContextRow('Diagnostics','View and share troubleshooting information')+settingContextRow('Privacy Policy',''))+'</div>';
}
function loadReferenceImage(base,retry=false) {
  const image=document.getElementById('current-ui-image');
  if(!retry&&image.dataset.route===base)return;
  image.dataset.route=base;
  image.hidden=false;
  document.getElementById('current-ui-error').hidden=true;
  image.src='assets/current-ui/'+base+'.png'+(retry?'?retry='+ ++referenceRetry:'');
}
const referenceImage=document.getElementById('current-ui-image');
referenceImage.addEventListener('load',()=>{
  referenceImage.hidden=false;
  document.getElementById('current-ui-error').hidden=true;
});
referenceImage.addEventListener('error',()=>{
  referenceImage.hidden=true;
  document.getElementById('current-ui-error').hidden=false;
});
function updateIntegrationContext(retry=false) {
  const guide=(area==='brew'||area==='learn')&&view!=='overview',base=guide?(entry==='learn'?'learn':'brew'):area;
  const image=document.getElementById('current-ui-image'),caption=document.getElementById('current-ui-caption');
  loadReferenceImage(base,retry);image.alt='Current installed Starlit Coffee '+nativeTitles[base]+' screen';
  caption.textContent=nativeTitles[base]+' · installed app capture'+(guide?' · existing entry screen':'');
  document.getElementById('current-reference').hidden=!compareCurrentUI;
  document.getElementById('preview-layout').classList.toggle('comparing',compareCurrentUI);
  const note=guide?'Guide detail is the proposed surface. The existing setup route remains in place.':area==='brew'?'Existing calculator and Start action. Active brew status uses the shared app dock.':area==='more'?'Existing four destinations. Active brew status stays outside this menu.':area==='learn'?'Existing grouped guide library. Only the three reviewed examples open in this preview.':area==='settings'?'Existing settings groups. Ready-reminder choices stay with each timer.':'Existing '+nativeTitles[area]+' layout. Brew activity is owned by the app.';
  document.getElementById('integration-context').textContent=previewSurface==='app'?note:'Outside the app · Android '+(previewSurface==='lock'?'lock-screen':'notification')+' example. Every running brew retains its clock and return action.';
}

// Paths copied unchanged from the native calculation_icon_*.xml resources.
const calculationIcons = {"coffee": "<svg viewBox=\"0 0 256 256\" fill=\"currentColor\" aria-hidden=\"true\"><path d=\"M151.875,150.313c-44.958-36.162-4.471-135.876,51.46-109.933c53.624,24.873,11.465,143.912-51.46,109.933ZM207,53c0-9.776-7.915-11.917-16.003-7.898c-28.401,14.111-18.914,51.337-27.817,75.579c-2.129,5.798-13.7,19.279-11.821,23.7c.561,1.32,1.517,2.722,2.766,3.494c4.037,2.495,10.967,1.337,14.644-1.169c24.978-17.029,16.012-46.096,23.418-70.784C195.022,66.47,200.121,59.879,207,53Z\"/><path d=\"M103,217C41.633,222.309-.31,120.267,54.694,97.909C115.875,73.04,171.497,198.056,103,217Zm2-8c8,1.872,11.98-6.085,13.059-13.009c5.218-33.467-33.15-44.399-49.301-64.112c-5.365-6.548-6.97-21.266-11.867-25.223c-1.408-1.139-4.62-1.298-6.4-.823c-.528,.141-1.008,.346-1.48,.618c-3.879,2.237-5.202,7.653-5.401,11.85c-1.496,31.608,34.103,40.249,49.253,60.689C99.924,188.516,97.561,201.437,105,209Z\"/></svg>", "water": "<svg viewBox=\"0 0 256 256\" fill=\"currentColor\" aria-hidden=\"true\"><path d=\"M126,37c7.459,0,13.118,14.708,17.378,21.001c28.895,42.689,71.182,84.005,33.777,136.619c-11.274,15.859-29.164,25.054-48.578,25.407c-17.727,.323-35.031-7.812-46.608-21.198C38.008,147.999,94.895,78.706,126,37Z\"/></svg>", "cup": "<svg viewBox=\"0 0 256 256\" fill=\"currentColor\" aria-hidden=\"true\"><path d=\"M187,92c10.614-.516,21.975-.156,30.438,7.25c18.656,16.327,9.117,51.682-8.996,64.444C197.835,171.167,184.632,173.05,172,174c-5.392,14.387-14.605,23.905-29.63,27.789c-18.234,4.714-49.111,6.067-66.127-3.172c-20.625-11.198-27.522-36.441-31.404-57.915c-2.983-16.506-9.555-50.176-1.458-65.066c17.942-32.992,117.617-30.259,138.954-1.939C186.391,79.081,186.787,85.508,187,92ZM58,120c9.438,1.203,109,1.257,111.306-.416c3.369-2.446,5.13-30.036,4.059-34.107C166.822,60.593,69.994,63.241,56,82c-3.445,4.618-1.817,12.782-1.541,18.229c.282,5.556-.785,15.445,3.541,19.771Zm121,38c20.786,1.732,35.109-13.022,35.327-33.124c.085-7.759-1.063-12.703-8.425-16.61c-4.418-2.344-15.817-4.163-19.902-.266c-1.291,1.232-7.77,48.461-7,50Z\"/></svg>"};
function calculationIcon(key) { return key==='cup' ? vesselIcon('cappuccino') : calculationIcons[key]; }
