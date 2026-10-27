'use strict';
function aiMenuInvite(){
 return `<aside class="ai-menu-invite"><img src="/assets/uteb-logo.png" alt="" width="48" height="48"><div><strong>Chưa biết ăn gì?</strong><p>Nói khẩu vị, mức calo hoặc ngân sách. Trợ lý AI sẽ tìm món phù hợp cho bạn.</p></div><a class="btn btn-outline-primary" href="#assistant">Gợi ý món với AI</a></aside>`;
}
function aiState(){return state.aiMenu||={messages:[],result:null,draft:'',storeId:state.storeId,busy:false,request:0};}
async function aiMenuPage(){
 const a=aiState();
 if(a.storeId!==state.storeId){a.storeId=state.storeId;a.messages=[];a.result=null;a.request++;a.busy=false;}
 let available=false;
 try{available=(await api('/public/ai/status')).available===true;}catch{ /* The menu remains usable if AI is unavailable. */ }
 a.available=available;
 return page('Hôm nay ăn gì cùng UTEB?',`Gợi ý từ thực đơn đang bán tại ${store()?.name||'cửa hàng'}.`,`
 <section class="ai-menu-panel" aria-label="Trợ lý gợi ý món"><div class="ai-menu-intro"><img src="/assets/uteb-logo.png" alt="" width="64" height="64"><div><div class="eyebrow">TRỢ LÝ AI UTEB</div><h2>Một bữa ăn đúng ý bạn.</h2><p>Thử: “Thèm bò, không cơm, tối đa 600 kcal và 100.000đ”.</p></div></div>
 <form id="ai-menu-form"><label for="ai-menu-message">Bạn muốn ăn như thế nào?</label><textarea id="ai-menu-message" name="message" class="form-control" rows="3" required maxlength="1000" placeholder="Mình thích gà, ăn nhẹ và không cay…" ${available?'':'disabled'}>${esc(a.draft)}</textarea>
 <div class="ai-menu-examples">${['Gà dưới 600 kcal, không cay','Thèm bò, tối đa 100.000đ','Ăn chay, ưu tiên ít calo','Bowl không cơm, không đậu'].map((text,index)=>`<button type="button" class="pill" data-action="ai-example" data-value="${index}" ${available?'':'disabled'}>${esc(text)}</button>`).join('')}</div>
 <div class="ai-menu-controls"><button type="submit" class="btn btn-primary" ${available&&!a.busy?'':'disabled'}>${a.busy?'Đang tìm món…':'Tìm món cho tôi'}</button><button type="button" class="btn btn-linkish" data-action="ai-reset">Bắt đầu lại</button><button type="button" class="btn-linkish" data-action="store">Đổi cửa hàng</button></div>
 <p class="small muted">Gợi ý dựa trên thực đơn đang bán và cấu hình nguyên liệu. Calo là ước tính cho một phần; giá chưa gồm phí giao hàng.</p></form>
 ${available?'':'<div class="notice" role="status">Trợ lý AI chưa sẵn sàng. Bạn vẫn có thể <a href="#menu">chọn món từ thực đơn</a>. <button type="button" class="small-link" data-action="retry">Thử lại</button></div>'}
 <div id="ai-menu-feedback" role="status" aria-live="polite"></div><div id="ai-menu-results">${aiMenuResults(a.result)}</div></section>`);
}
function aiMenuResults(result){
 if(!result)return '';
 return `<div class="ai-menu-answer"><h2>Gợi ý cho bạn</h2><p>${esc(result.message)}</p><div class="ai-menu-criteria">${(result.criteria||[]).map(c=>`<span>${esc(c)}</span>`).join('')}</div></div>
 <div class="ai-menu-cards">${(result.items||[]).map((item,index)=>`<article class="ai-menu-card"><img src="${esc(item.image)}" alt="${esc(item.name)}" loading="lazy"><div><span class="eyebrow">${esc(item.category)}</span><h3>${esc(item.name)}</h3><p class="ai-menu-customization">${esc(item.customization)}</p><p class="small muted">${esc(item.reason)}</p><div class="ai-menu-numbers"><strong>${money(item.price)}</strong><span>${esc(item.calories)} kcal / phần</span></div><button type="button" class="btn btn-primary" data-action="ai-choose" data-value="${index}">Xem cấu hình gợi ý</button></div></article>`).join('')}</div>`;
}
async function submitAiMenu(form){
 const a=aiState(),message=String(new FormData(form).get('message')||'').trim();
 requireClient(message.length>0&&message.length<=1000,'Hãy mô tả nhu cầu trong 1–1.000 ký tự.');
 if(a.busy)return;
 const request=++a.request,storeId=state.storeId,feedback=document.querySelector('#ai-menu-feedback');
 a.busy=true;a.draft=message;
 if(feedback){feedback.textContent='Đang tìm món phù hợp với bạn…';feedback.className='muted';}
 try{
  const result=await api('/public/ai/recommend','POST',{storeId,message,previousMessages:a.messages.slice(-4)});
  if(request!==a.request||storeId!==state.storeId)return;
  a.result=result;a.messages=[...a.messages,message].slice(-4);
  if(location.hash==='#assistant'){
   const panel=document.querySelector('#ai-menu-results');if(panel)panel.innerHTML=aiMenuResults(result);
   if(feedback)feedback.textContent='';
  }
 }catch(error){
  if(request===a.request&&location.hash==='#assistant'&&feedback){feedback.textContent=error.message;feedback.className='error-message';}
 }finally{if(request===a.request)a.busy=false;}
}
async function chooseAiMenu(value){
 const a=aiState(),suggestion=a.result?.items?.[Number(value)];
 requireClient(suggestion&&suggestion.storeId===state.storeId,'Cửa hàng đã thay đổi. Hãy tìm lại gợi ý.');
 await reloadData();
 const p=product(suggestion.productId);
 requireClient(p&&p.storeId===state.storeId&&p.available&&p.stock>0,'Món không còn bán. Hãy tìm lại gợi ý.');
 const ids=(suggestion.ingredientIds||[]).map(Number);
 requireClient(ids.every(id=>state.data.ingredients.some(i=>i.id===id&&i.available)),'Nguyên liệu vừa thay đổi. Hãy tìm lại gợi ý.');
 openBuilder(p.id,null,'cart',ids);
 const current=calculateBuilder(p,ids);
 if(current.price!==Number(suggestion.price)||current.kcal!==suggestion.calories)toast('Giá hoặc calo vừa được cập nhật. Hãy kiểm tra cấu hình trước khi thêm vào giỏ.');
}
