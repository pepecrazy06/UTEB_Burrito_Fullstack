'use strict';
function statisticsFilters(){
 if(state.statisticsOwner!==state.user?.id){state.statisticsOwner=state.user?.id;state.statisticsFilters={startDate:'',endDate:'',storeId:''};state.statisticsStores=[];}
 return state.statisticsFilters||={startDate:'',endDate:'',storeId:''};
}
function statisticsQuery(filters){
 for(const [key,label]of [['startDate','Từ ngày'],['endDate','Đến ngày']]){
  const value=filters[key];if(!value)continue;
  const parsed=new Date(value+'T00:00:00Z');
  if(!/^[0-9]{4}-[0-9]{2}-[0-9]{2}$/.test(value)||value.slice(0,4)==='0000'||Number.isNaN(parsed.getTime())||parsed.toISOString().slice(0,10)!==value)throw Error(label+' không hợp lệ. Dùng định dạng YYYY-MM-DD.');
 }
 if(filters.startDate&&filters.endDate&&filters.startDate>filters.endDate)throw Error('Từ ngày không được lớn hơn đến ngày.');
 if(filters.storeId&&!/^[1-9][0-9]*$/.test(String(filters.storeId)))throw Error('Mã chi nhánh không hợp lệ.');
 const params=new URLSearchParams();for(const name of ['startDate','endDate','storeId'])if(filters[name])params.set(name,filters[name]);
 return params.size?'?'+params.toString():'';
}
function statisticsForm(filters,stores){
 const defaultLabel=has('ADMIN')?'Tất cả chi nhánh':'Phạm vi được cấp';
 return `<form id="statistics-filter-form" class="panel mb-4"><h2 class="h5 fw-bold">Bộ lọc thống kê</h2><div class="form-grid">
  ${field('startDate','Từ ngày',filters.startDate,'date',false)}${field('endDate','Đến ngày',filters.endDate,'date',false)}
  ${select('storeId','Chi nhánh',[['',defaultLabel],...stores.map(store=>[String(store.id),store.name+(store.active?'':' (ngừng hoạt động)')])],String(filters.storeId||''),false)}
  </div><p class="small muted">Lọc theo ngày tạo đơn, giờ Việt Nam (UTC+7), bao gồm cả ngày kết thúc. Có thể để trống một phía.</p>
  <div class="inline-actions"><button type="submit" class="btn btn-primary">Áp dụng bộ lọc</button><button type="button" class="btn btn-outline-primary" data-action="statistics-reset">Đặt lại bộ lọc</button></div>
  <p id="statistics-filter-error" class="error-message mt-3" role="alert" hidden></p></form>`;
}
function statisticsScope(filters,stores){
 const branch=filters.storeId?stores.find(store=>String(store.id)===String(filters.storeId))?.name:(has('ADMIN')?'Tất cả chi nhánh':'Phạm vi được cấp');
 return `Đang áp dụng: ${filters.startDate||'Không giới hạn đầu kỳ'} → ${filters.endDate||'Không giới hạn cuối kỳ'} · ${branch||'Chi nhánh đã chọn'} · Giờ Việt Nam`;
}
function statisticsResults(data,filters){
 const stores=data.availableStores||[];
 return `<p class="muted small">${esc(statisticsScope(filters,stores))}</p>
  <p class="small">${data.assignedOnly?'Thống kê chỉ gồm đơn được phân công giao, không gồm đơn tự mua. ':''}Doanh thu ghi nhận từ đơn hoàn tất và đã thanh toán, loại trừ đơn hủy hoặc đã hoàn tiền. ${data.completedOrders} đơn đủ điều kiện.</p>
  <div class="stats"><div class="stat"><span>Doanh thu ghi nhận</span><strong>${money(data.revenue)}</strong></div><div class="stat"><span>Tổng đơn</span><strong>${data.totalOrders}</strong></div><div class="stat"><span>Khách đã đặt</span><strong>${data.customers}</strong></div><div class="stat"><span>Hoa hồng</span><strong>${money(data.commission)}</strong></div></div>
  <p class="small muted">Doanh thu trừ hoa hồng: ${money(data.net)} · Chi nhánh có đơn: ${data.stores}</p>
  ${data.totalOrders===0?'<p class="demo-note" role="status">Không có đơn hàng trong phạm vi lọc. Các chỉ số bằng 0.</p>':''}
  <section class="panel mb-4"><h3 class="h5">Đơn theo trạng thái</h3>${Object.entries(data.byStatus||{}).map(([status,count])=>`<span class="badge-label me-2 mb-2">${esc(statuses[status]||status)}: ${Number(count)}</span>`).join('')||'<p class="muted">Chưa có đơn.</p>'}</section><div class="two-col"><section class="panel"><h3 class="h5 fw-bold">Doanh thu theo ngày</h3><p class="small muted">8 ngày có doanh thu cao nhất trong phạm vi lọc.</p>${barChart(data.daily,money)}</section>
  <section class="panel"><h3 class="h5 fw-bold">Món bán chạy</h3><p class="small muted">8 món bán chạy từ các đơn ghi nhận doanh thu.</p>${barChart(data.topProducts,n=>n+' phần')}</section></div>
  <section class="panel mt-4"><h3 class="h4 fw-bold">5 đơn gần nhất trong phạm vi lọc</h3><div class="table-wrap"><table class="table"><thead><tr><th>Đơn</th><th>Ngày tạo</th><th>Chi nhánh</th><th>Trạng thái</th><th>Thanh toán</th><th>Tổng tiền</th></tr></thead><tbody>
  ${data.orders.map(order=>`<tr><td><a href="#order/${order.id}" class="small-link">${esc(order.code)}</a></td><td>${esc(order.createdAt?.replace('T',' ')||'')}</td><td>${esc(stores.find(store=>store.id===order.storeId)?.name||'Chi nhánh')}</td><td>${esc(statuses[order.status]||order.status)}</td><td>${esc(statuses[order.paymentStatus]||order.paymentStatus)}</td><td>${money(order.total)}</td></tr>`).join('')||'<tr><td colspan="6" class="muted">Không có đơn hàng.</td></tr>'}
  </tbody></table></div></section>`;
}
async function statisticsDashboard(){
 const filters={...statisticsFilters()},owner=state.user?.id,seq=state.statisticsRequestVersion=(state.statisticsRequestVersion||0)+1;
 const target=document.querySelector('#statistics-results');if(target)target.innerHTML='<p class="muted" role="status">Đang tải thống kê theo bộ lọc…</p>';
 try{
  const data=await api('/admin/dashboard'+statisticsQuery(filters));
  if(seq!==state.statisticsRequestVersion||owner!==state.user?.id)return '';
  state.statisticsStores=data.availableStores||[];
  return statisticsForm(filters,state.statisticsStores)+`<div id="statistics-results">${statisticsResults(data,filters)}</div>`;
 }catch(error){
  if(seq!==state.statisticsRequestVersion||owner!==state.user?.id)return '';
  return statisticsForm(filters,state.statisticsStores||[])+`<div id="statistics-results"><p class="error-message" role="alert">${esc(error.message)}</p><p class="muted">Chưa tải được kết quả cho bộ lọc này. Vui lòng thử lại hoặc đặt lại bộ lọc.</p></div>`;
 }
}
async function applyStatisticsFilters(form){
 const values=formData(form),filters={startDate:values.startDate||'',endDate:values.endDate||'',storeId:values.storeId||''};
 try{statisticsQuery(filters);}catch(error){const target=form.querySelector('#statistics-filter-error');target.textContent=error.message;target.hidden=false;throw error;}
 statisticsFilters();state.statisticsFilters=filters;await render();
}
async function resetStatisticsFilters(){statisticsFilters();state.statisticsFilters={startDate:'',endDate:'',storeId:''};await render();}
