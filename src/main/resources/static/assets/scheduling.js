'use strict';
const schedulingZone='Asia/Ho_Chi_Minh';
function vietnamDateTime(epoch=Date.now()){
 const parts=new Intl.DateTimeFormat('en-GB',{timeZone:schedulingZone,year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).formatToParts(new Date(epoch));
 const values=Object.fromEntries(parts.map(part=>[part.type,part.value]));return `${values.year}-${values.month}-${values.day}T${values.hour}:${values.minute}`;
}
function captureCheckoutDraft(form=document.querySelector('#checkout-form')){
 if(!form||form.dataset.completed==='true'||!state.user)return;
 const fields=formData(form);fields.scheduledAt=form.elements.scheduledAt.value;
 state.checkoutDraft={userId:state.user.id,fields};
}
function restoreCheckoutDraft(){
 const form=document.querySelector('#checkout-form'),draft=state.checkoutDraft;
 if(!form||!draft||draft.userId!==state.user?.id)return;
 for(const [name,value]of Object.entries(draft.fields)){
  const input=form.elements[name];if(!input)continue;
  if(input.type==='checkbox')input.checked=Boolean(value);else input.value=value;
 }
}
function showCheckoutError(message=''){
 const error=document.querySelector('#checkout-error');if(!error)return;
 error.textContent=message;error.hidden=!message;
}
function checkoutStores(){
 const ids=[...new Set(state.cart.map(row=>Number(row.line.storeId)))];
 return ids.map(id=>state.data.stores.find(store=>store.id===id)).filter(Boolean);
}
function showCheckoutHours(stores=checkoutStores()){
 const target=document.querySelector('#checkout-hours');if(!target)return;
 target.innerHTML=`<strong>Chi nhánh chuẩn bị đơn và giờ hoạt động:</strong>${stores.map(store=>`<div>${esc(store.name)}: ${esc(store.hours||'Chưa có giờ hoạt động hợp lệ')}</div>`).join('')}
  <div class="mt-2">Giờ hẹn tính theo giờ Việt Nam (UTC+7). Nhận từ giờ mở cửa, không nhận đúng giờ đóng cửa. Hẹn sau hơn ${state.data.businessRules?.schedule_lead_minutes??30} phút và trước ${state.data.businessRules?.schedule_max_days??7} ngày; giỏ nhiều chi nhánh cần phù hợp với tất cả chi nhánh.</div>`;
}
function syncScheduleFields(){
 const form=document.querySelector('#checkout-form');if(!form)return;
 const input=form.elements.scheduledAt,scheduled=form.elements.scheduleMode.value==='SCHEDULE';
 input.parentElement.hidden=!scheduled;input.required=scheduled;input.disabled=!scheduled;input.step='60';
 input.min=vietnamDateTime(Date.now()+(Number(state.data.businessRules?.schedule_lead_minutes??30)+1)*60000);input.max=vietnamDateTime(Date.now()+Number(state.data.businessRules?.schedule_max_days??7)*86400000-1);
 input.setAttribute('aria-describedby','checkout-hours checkout-error');
 const delivery=document.querySelector('#delivery-fields');if(delivery)delivery.hidden=form.elements.type.value==='PICKUP';
 showCheckoutHours();
}
