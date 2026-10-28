'use strict';
const cancellationLabels={REQUESTED:'Chờ duyệt hủy',APPROVED:'Đã duyệt hủy · Chờ hoàn tiền',REJECTED:'Đã từ chối hủy',REFUNDED:'Đã xác nhận hoàn tiền'};
// Keep refund access for historical online orders; checkout no longer offers MoMo.
function canRequestCancellation(order){
 return state.user?.id===order.userId&&['NEW','CONFIRMED'].includes(order.status)&&order.paymentStatus==='PAID'
  &&['VNPAY','MOMO'].includes(order.paymentMethod)&&!order.cancellationStatus;
}
function cancellationState(status){return status?`<p class="small fw-bold text-wine mt-2">${esc(cancellationLabels[status]||status)}</p>`:'';}
function cancellationPanel(data,order){
 const request=data?.request;
 if(!request)return cancellationState(data?.status);
 const canManage=has('ADMIN')||(has('MANAGER')&&state.user.storeId===order.storeId);
 return `<section class="panel mt-4"><h2 class="h5 fw-bold">Yêu cầu hủy đơn đã thanh toán</h2>
  ${cancellationState(request.status)}<p>${esc(request.reason)}</p>
  <p class="small">Số tiền cần hoàn: <strong>${money(request.amount)}</strong></p>
  <p class="date-info">Gửi lúc ${date(request.createdAt)}</p>
  ${request.decidedAt?`<p class="small">Xử lý lúc ${date(request.decidedAt)} · Tài khoản #${esc(request.decidedBy)}<br>${esc(request.decisionNote||'')}</p>`:''}
  ${request.status==='APPROVED'?'<p class="demo-note">Đơn đã được hủy. Tiền đang chờ quản trị viên hoàn thủ công; hệ thống chưa xác nhận đã trả tiền.</p>':''}
  ${request.status==='REFUNDED'?`<div class="demo-note"><strong>Đã xác nhận hoàn ${money(request.confirmedAmount)}</strong><br>Thời gian: ${date(request.refundedAt)}<br>Mã giao dịch: ${esc(request.refundReference)}<br>Bằng chứng: ${esc(request.evidence)}<br>${esc(request.refundNote||'')}<br>Người xác nhận: #${esc(request.refundedBy)}</div>`:''}
  ${canManage&&request.status==='REQUESTED'?`<button class="btn btn-primary btn-sm" data-action="cancellation-decision" data-order-id="${order.id}">Duyệt / từ chối</button>`:''}
  ${canManage&&request.status==='APPROVED'?`<button class="btn btn-primary btn-sm" data-action="cancellation-refund" data-order-id="${order.id}">Xác nhận hoàn tiền thủ công</button>`:''}
 </section>`;
}
async function cancellationAdminPage(){
 const filter=state.cancellationFilter||'';
 const rows=await api('/admin/cancellations'+(filter?'?status='+encodeURIComponent(filter):''));
 return `<div class="section-heading"><h2 class="h3 fw-bold">Hủy đơn đã thanh toán</h2></div>
  <p class="muted">Duyệt hủy và xác nhận hoàn tiền là hai bước riêng. Thực hiện chuyển tiền ngoài hệ thống trước khi lưu chứng từ.</p>
  <label for="cancellation-filter" class="form-label">Lọc yêu cầu</label><select id="cancellation-filter" class="form-select mb-4"><option value="">Tất cả</option>
  ${Object.entries(cancellationLabels).map(([value,label])=>`<option value="${value}" ${filter===value?'selected':''}>${label}</option>`).join('')}</select>
  ${rows.map(({request,order})=>`<article class="mb-4"><div class="panel"><a href="#order/${order.id}" class="small-link">${esc(order.code)}</a>
   <p class="small mt-2">Đơn: ${esc(statuses[order.status]||order.status)} · Thanh toán: ${esc(statuses[order.paymentStatus]||order.paymentStatus)} · ${esc(order.paymentMethod)}</p>
   <p class="small">Đã thanh toán: ${money(request.amount)} · Giao dịch gốc: ${esc(request.paymentReference)}</p></div>${cancellationPanel({request},order)}</article>`).join('')||'<p class="muted">Không có yêu cầu ở trạng thái này.</p>'}`;
}
function openCancellationRequest(orderId){
 showModal(`<h2 class="h4 fw-bold">Yêu cầu hủy đơn</h2><p>Chỉ áp dụng trước khi chế biến. Gửi yêu cầu chưa có nghĩa đã được hủy hoặc hoàn tiền.</p>
  <form id="cancellation-request-form"><input type="hidden" name="orderId" value="${Number(orderId)}">${textArea('reason','Lý do hủy (20–2000 ký tự)','',20)}
  <button type="submit" class="btn btn-primary">Gửi yêu cầu hủy</button></form>`);
 content.querySelector('[name="reason"]').maxLength=2000;
}
async function openCancellationDecision(orderId){
 const data=await api('/orders/'+Number(orderId)+'/cancellation'),request=data.request;
 requireClient(request?.status==='REQUESTED','Yêu cầu đã được xử lý. Vui lòng tải lại.');
 showModal(`<h2 class="h4 fw-bold">Xét duyệt yêu cầu hủy</h2><p>${esc(request.reason)}</p><p>Số tiền chờ hoàn nếu duyệt: ${money(request.amount)}</p>
  <form id="cancellation-decision-form"><input type="hidden" name="id" value="${request.id}">
  ${select('status','Quyết định',[['APPROVED','Duyệt hủy — chờ hoàn tiền'],['REJECTED','Từ chối hủy']])}
  ${textArea('note','Phản hồi (bắt buộc khi từ chối)')}<button type="submit" class="btn btn-primary">Lưu quyết định</button></form>`);
 content.querySelector('[name="note"]').maxLength=1000;
}
async function openCancellationRefund(orderId){
 const data=await api('/orders/'+Number(orderId)+'/cancellation'),request=data.request;
 requireClient(request?.status==='APPROVED','Chỉ xác nhận hoàn tiền cho yêu cầu đã được duyệt.');
 showModal(`<h2 class="h4 fw-bold">Xác nhận hoàn tiền thủ công</h2><p>Hệ thống không tự chuyển tiền. Chỉ xác nhận sau khi đã hoàn tiền cho khách.</p>
  <form id="cancellation-refund-form"><input type="hidden" name="id" value="${request.id}">
  ${field('amount','Số tiền hoàn (VND)',request.amount,'number')}${field('reference','Mã giao dịch hoàn tiền')}
  ${field('refundedAt','Thời gian đã hoàn tiền',localDate(0).slice(0,19),'datetime-local')}
  ${textArea('evidence','Bằng chứng văn bản (8–255 ký tự)','',8)}${textArea('note','Ghi chú')}
  <label class="mb-3"><input type="checkbox" name="confirmed" required> Tôi xác nhận đã hoàn đủ tiền thủ công ngoài hệ thống.</label>
  <button type="submit" class="btn btn-primary">Lưu bằng chứng và xác nhận</button></form>`);
 const form=content.querySelector('form');form.elements.amount.readOnly=true;form.elements.amount.step='0.01';form.elements.refundedAt.step='1';
 form.elements.reference.maxLength=120;form.elements.evidence.maxLength=255;form.elements.note.maxLength=1000;
}
document.addEventListener('change',async event=>{
 if(event.target.id==='cancellation-filter'){state.cancellationFilter=event.target.value;await render();}
});
