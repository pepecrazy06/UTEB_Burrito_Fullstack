'use strict';
const aiMenuExamples=['Gà dưới 600 kcal, không cay','Thèm bò, tối đa 100.000đ','Ăn chay, ưu tiên ít calo','Bowl không cơm, không đậu'];
function aiState(){
 const a=state.aiMenu||={messages:[],turns:[],result:null,draft:'',storeId:state.storeId,busy:false,request:0,nextTurn:0,available:null};
 a.turns||=[];a.nextTurn||=0;if(a.storeId===undefined)a.storeId=state.storeId;
 if(a.storeId!==state.storeId){a.storeId=state.storeId;a.messages=[];a.turns=[];a.result=null;a.request++;a.busy=false;a.draft='';}
 return a;
}
function aiMenuPage(){return page('Một bữa ăn đúng ý bạn.',`Trợ lý UTEB luôn ở đây để cùng bạn chọn món tại ${store()?.name||'cửa hàng'}.`,`<section class="ai-assistant-welcome"><img src="/assets/uteb-logo.png" alt="" width="100" height="100"><div class="eyebrow">TRỢ LÝ AI UTEB</div><h2>Hôm nay bạn thèm gì?</h2><p>Gà hay bò? Ăn nhẹ hay một bữa thật no?<br>Trò chuyện để tìm món theo khẩu vị, calo và ngân sách của bạn.</p><button type="button" class="btn btn-primary" data-action="ai-open">Mở cửa sổ trò chuyện</button><p class="small muted">Bạn cũng có thể bấm biểu tượng UTEB ở góc dưới màn hình.</p></section>`);}
function aiPromptButtons(){return aiMenuExamples.map((text,index)=>`<button type="button" data-action="ai-example" data-value="${index}"><span>${esc(text)}</span><span aria-hidden="true">↗</span></button>`).join('');}
function aiChatMarkup(){return `<div class="ai-chat-shell"><aside class="ai-chat-sidebar"><div class="ai-chat-brand"><img src="/assets/uteb-logo.png" alt="" width="76" height="76"><div><span>UTEB BURRITO</span><h2>Chọn món<br>hợp gu bạn.</h2></div></div><p class="ai-chat-description">Một chút cảm hứng cho bữa ăn hôm nay. Kể cho UTEB nghe bạn đang muốn ăn gì nhé.</p><div class="ai-chat-store"><span class="eyebrow">CỬA HÀNG CỦA BẠN</span><strong>${esc(store()?.name||'Chọn cửa hàng')}</strong><button type="button" data-action="ai-store">Đổi cửa hàng <span aria-hidden="true">↗</span></button></div><h3>Bắt đầu từ điều bạn thích</h3><div class="ai-chat-prompts">${aiPromptButtons()}</div><p class="ai-chat-sidebar-note">Gợi ý từ món đang bán, theo đúng cấu hình nguyên liệu bạn chọn.</p></aside><section class="ai-chat-main"><header class="ai-chat-heading"><div class="ai-chat-title"><img src="/assets/uteb-logo.png" alt="" width="42" height="42"><div><h2 id="ai-chat-title">Trợ lý UTEB</h2><span id="ai-chat-status">Đang kiểm tra kết nối…</span></div></div><div class="ai-chat-header-actions"><button type="button" class="ai-chat-new" data-action="ai-reset" aria-label="Cuộc trò chuyện mới" title="Cuộc trò chuyện mới"><svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M20 11a8 8 0 1 1-2.4-5.7M20 3v5h-5"/></svg></button><button type="button" class="ai-chat-close" data-action="ai-close" aria-label="Đóng trợ lý">×</button></div></header><div class="ai-chat-mobile-prompts">${aiPromptButtons()}</div><div id="ai-chat-messages" class="ai-chat-messages" role="log" aria-label="Cuộc trò chuyện với trợ lý UTEB" aria-live="polite" aria-relevant="additions text"></div><div class="ai-chat-compose"><div id="ai-chat-availability" class="ai-chat-availability" role="status"></div><button id="ai-chat-reconnect" class="small-link" type="button" data-action="ai-reconnect">Thử kết nối lại</button><form id="ai-menu-form"><label class="visually-hidden" for="ai-menu-message">Tin nhắn cho trợ lý UTEB</label><textarea id="ai-menu-message" name="message" rows="2" required maxlength="1000" placeholder="Mình thèm gà, ăn nhẹ và không cay…"></textarea><button id="ai-chat-send" type="submit" aria-label="Gửi tin nhắn" disabled><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="m4 12 16-8-6 16-3-7-7-1Z"/><path d="m11 13 9-9"/></svg></button></form><p>Calo ước tính cho một phần · Giá chưa gồm phí giao hàng</p></div></section></div>`;}
let aiStatusTimer=null;
function aiChatDialog(){return typeof document==='undefined'?null:document.querySelector('#ai-chat-dialog');}
function aiChatVisible(){return !!aiChatDialog()?.open;}
async function openAiChat(){
 requireClient(!!state.data,'Thực đơn đang được tải. Bạn hãy thử lại sau một chút.');
 const dialog=aiChatDialog();aiState();if(!dialog)return;
 dialog.innerHTML=aiChatMarkup();
 if(!dialog.open){document.body.style.setProperty('--ai-chat-scrollbar-gap',Math.max(0,window.innerWidth-document.documentElement.clientWidth)+'px');document.body.style.setProperty('--ai-chat-base-padding',window.getComputedStyle(document.body).paddingRight);dialog.showModal();}
 document.querySelector('#ai-assistant-launcher')?.setAttribute('aria-expanded','true');
 paintAiChat(true);document.querySelector('#ai-menu-message')?.focus();clearInterval(aiStatusTimer);
 aiStatusTimer=setInterval(()=>{if(aiChatVisible())void checkAiMenuStatus();else clearInterval(aiStatusTimer);},8000);
 await checkAiMenuStatus();
}
function closeAiChat(){
 if(typeof document==='undefined')return;
 const dialog=aiChatDialog();if(dialog?.open)dialog.close();document.body.style.removeProperty('--ai-chat-scrollbar-gap');document.body.style.removeProperty('--ai-chat-base-padding');clearInterval(aiStatusTimer);aiStatusTimer=null;
 const launcher=document.querySelector('#ai-assistant-launcher');launcher?.setAttribute('aria-expanded','false');launcher?.focus();
}
async function checkAiMenuStatus(){
 const a=aiState();if(a.checkingStatus)return;a.checkingStatus=true;paintAiChat();
 try{a.available=(await api('/public/ai/status')).available===true;}catch{a.available=false;}
 finally{a.checkingStatus=false;paintAiChat();}
}
function aiMenuResults(result,turnId=null){
 if(!result)return '';const ref=turnId===null?'':` data-turn="${Number(turnId)}"`;
 return `<p class="ai-chat-answer">${esc(result.message)}</p><div class="ai-menu-criteria">${(result.criteria||[]).map(c=>`<span>${esc(c)}</span>`).join('')}</div><div class="ai-menu-cards">${(result.items||[]).map((item,index)=>`<article class="ai-menu-card"><img src="${esc(item.image)}" alt="${esc(item.name)}" loading="lazy"><div><span class="eyebrow">${esc(item.category)}</span><h3>${esc(item.name)}</h3><p class="ai-menu-customization">${esc(item.customization)}</p><div class="ai-menu-numbers"><strong>${money(item.price)}</strong><span>${esc(item.calories)} kcal / phần</span></div><button type="button" class="btn btn-primary" data-action="ai-choose" data-value="${index}"${ref}>Xem cấu hình món</button></div></article>`).join('')}</div>`;
}
function aiChatTranscript(a){return `<div class="ai-chat-greeting"><img src="/assets/uteb-logo.png" alt="" width="34" height="34"><div><span class="ai-chat-speaker">TRỢ LÝ UTEB</span><p>Chào bạn! Hôm nay bạn muốn ăn gì?</p><p>Cho mình biết loại nhân yêu thích, mức calo hoặc ngân sách. Mình sẽ tìm món phù hợp tại <strong>${esc(store()?.name||'cửa hàng bạn chọn')}</strong>.</p></div></div>`+a.turns.map(turn=>`<div class="ai-chat-user"><span class="ai-chat-speaker">BẠN</span><p>${esc(turn.user)}</p></div>${turn.result?`<div class="ai-chat-response"><span class="ai-chat-speaker">TRỢ LÝ UTEB</span>${aiMenuResults(turn.result,turn.id)}</div>`:turn.error?`<div class="ai-chat-error" role="status">${esc(turn.error)}</div>`:''}`).join('')+(a.busy?'<div class="ai-chat-thinking" role="status"><span aria-hidden="true">● ● ●</span> Đang tìm món hợp gu bạn…</div>':'');}
function paintAiChat(scroll=false){
 if(!aiChatVisible())return;
 const a=aiState(),log=document.querySelector('#ai-chat-messages'),input=document.querySelector('#ai-menu-message');
 if(log){const html=aiChatTranscript(a);if(log.aiTranscript!==html){log.innerHTML=html;log.aiTranscript=html;}if(scroll)log.scrollTop=log.scrollHeight;}
 const status=document.querySelector('#ai-chat-status');if(status){status.textContent=a.busy?'Đang tìm món cho bạn…':a.available?'Sẵn sàng cùng bạn chọn món':a.checkingStatus?'Đang kiểm tra kết nối…':'Chưa kết nối với trợ lý';status.classList.toggle('is-ready',a.available===true);}
 const notice=document.querySelector('#ai-chat-availability');if(notice)notice.textContent=a.available?'':a.checkingStatus?'Đang kết nối trợ lý…':'Trợ lý chưa sẵn sàng. Bạn có thể soạn tin nhắn trước rồi thử kết nối lại.';
 const retry=document.querySelector('#ai-chat-reconnect');if(retry){retry.hidden=a.available===true;retry.disabled=!!a.checkingStatus;}
 const send=document.querySelector('#ai-chat-send');if(send)send.disabled=!a.available||a.busy;
 const reset=document.querySelector('.ai-chat-new');if(reset)reset.disabled=a.busy;
 if(input&&input.value!==a.draft)input.value=a.draft;
}
function aiExample(value){const text=aiMenuExamples[Number(value)];if(!text)return;aiState().draft=text;paintAiChat();document.querySelector('#ai-menu-message')?.focus();}
function resetAiChat(){const a=aiState();if(a.busy)return;a.request++;a.messages=[];a.turns=[];a.result=null;a.draft='';paintAiChat(true);document.querySelector('#ai-menu-message')?.focus();}
async function submitAiMenu(form){
 const a=aiState(),message=String(new FormData(form).get('message')||'').trim();requireClient(message.length>0&&message.length<=1000,'Hãy mô tả nhu cầu trong 1–1.000 ký tự.');
 if(a.busy)return;requireClient(a.available===true,'Trợ lý chưa sẵn sàng. Bạn hãy thử kết nối lại sau.');
 const request=++a.request,storeId=state.storeId,turn={id:++a.nextTurn,user:message,result:null,error:null},previous=a.messages.slice(-4);
 a.turns.push(turn);a.turns=a.turns.slice(-12);a.busy=true;a.draft='';paintAiChat(true);
 try{const result=await api('/public/ai/recommend','POST',{storeId,message,previousMessages:previous});if(request!==a.request||storeId!==state.storeId)return;turn.result=result;a.result=result;a.messages=[...a.messages,message].slice(-4);}
 catch(error){if(request===a.request&&storeId===state.storeId){turn.error=error.message;if(error.status===503)a.available=false;}}
 finally{if(request===a.request){a.busy=false;paintAiChat(true);}}
}
async function chooseAiMenu(value,turnId=null){
 const a=aiState(),result=turnId===null?a.result:a.turns.find(turn=>turn.id===Number(turnId))?.result,suggestion=result?.items?.[Number(value)];
 requireClient(suggestion&&suggestion.storeId===state.storeId,'Cửa hàng đã thay đổi. Hãy tìm lại gợi ý.');await reloadData();
 const p=product(suggestion.productId);requireClient(p&&p.storeId===state.storeId&&p.available&&p.stock>0,'Món không còn bán. Hãy tìm lại gợi ý.');
 const ids=(suggestion.ingredientIds||[]).map(Number);requireClient(ids.every(id=>state.data.ingredients.some(i=>i.id===id&&i.available)),'Nguyên liệu vừa thay đổi. Hãy tìm lại gợi ý.');
 closeAiChat();openBuilder(p.id,null,'cart',ids);const current=calculateBuilder(p,ids);
 if(current.price!==Number(suggestion.price)||current.kcal!==suggestion.calories)toast('Giá hoặc calo vừa được cập nhật. Hãy kiểm tra cấu hình trước khi thêm vào giỏ.');
}
function installAiChatEvents(){
 const dialog=aiChatDialog();if(!dialog)return;
 dialog.addEventListener('cancel',event=>{event.preventDefault();closeAiChat();});
 dialog.addEventListener('click',event=>{if(event.target!==dialog)return;const box=dialog.getBoundingClientRect();if(event.clientX<box.left||event.clientX>box.right||event.clientY<box.top||event.clientY>box.bottom)closeAiChat();});
 dialog.addEventListener('keydown',event=>{if(event.target.id==='ai-menu-message'&&event.key==='Enter'&&!event.shiftKey&&!event.isComposing){event.preventDefault();const a=aiState();if(a.available&&!a.busy)event.target.form.requestSubmit();}});
}
if(typeof document!=='undefined')document.addEventListener('DOMContentLoaded',installAiChatEvents);
