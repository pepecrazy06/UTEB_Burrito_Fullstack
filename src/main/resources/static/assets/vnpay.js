'use strict';

async function launchPayment(id) {
  const payment = await api('/orders/' + id + '/payment', 'POST', {});
  if (payment.simulation) {
    showModal(`<h2 class="h4 fw-bold">Thanh toán giả lập local</h2><p>Thao tác này phục vụ demo, không chuyển tiền thật.</p><div class="inline-actions"><button class="btn btn-primary" data-action="simulate" data-id="${id}" data-value="true">Giả lập thành công</button><button class="btn btn-outline-primary" data-action="simulate" data-id="${id}" data-value="false">Giả lập thất bại</button></div>`);
    return;
  }
  const target = new URL(payment.url);
  requireClient(target.protocol === 'https:' && ['sandbox.vnpayment.vn', 'test-payment.momo.vn'].includes(target.hostname)
    && !target.username && !target.password, 'Địa chỉ thanh toán không hợp lệ.');
  location.assign(target.href);
}

async function vnpayResultPage(id, outcome) {
  if (!/^\d+$/.test(id || '')) return page('Không xác minh được kết quả thanh toán', '',
    '<div class="panel"><p>Thông tin quay về không hợp lệ. Hãy kiểm tra trạng thái đơn trong tài khoản trước khi thanh toán lại.</p><a class="btn btn-primary" href="#orders">Xem đơn hàng</a></div>');
  if (!state.user) {
    state.paymentReturnRoute = '#payment-result/' + id + '/' + (outcome || 'received');
    return loginRequired();
  }
  const data = await api('/orders/' + id), order = data.order;
  let title, message;
  const pending = order.status === 'NEW' && order.paymentStatus === 'PENDING';
  if (order.paymentStatus === 'PAID') {
    title = 'Thanh toán thành công'; message = 'Cửa hàng đã xác nhận thanh toán cho đơn của bạn.';
  } else if (order.paymentStatus === 'REVIEW_REQUIRED') {
    title = 'Thanh toán cần đối soát'; message = 'VNPay đã báo thu tiền nhưng đơn cần được cửa hàng kiểm tra. Vui lòng liên hệ cửa hàng và không thanh toán lại đơn này.';
  } else if (order.paymentStatus === 'REFUNDED') {
    title = 'Đơn đã được hoàn tiền'; message = 'Xem chi tiết xử lý hoàn tiền trong đơn hàng.';
  } else if (order.status === 'CANCELLED') {
    title = 'Đơn đã hủy hoặc hết hạn'; message = 'Đơn này đã ngừng xử lý. Nếu ngân hàng đã trừ tiền, vui lòng liên hệ cửa hàng để đối soát.';
  } else if (pending) {
    title = 'Đang xác nhận thanh toán'; message = 'Đang chờ kết quả xác nhận. Trang sẽ tự cập nhật; bạn chưa cần thực hiện thêm lần thanh toán.';
  } else {
    title = 'Thanh toán chưa thành công'; message = outcome === 'cancelled'
      ? 'Bạn đã hủy lần thanh toán này. Có thể thử lại khi đơn còn thời hạn.'
      : 'Giao dịch chưa hoàn tất. Kiểm tra thông tin và thử lại khi đơn còn thời hạn.';
  }
  if (state.paymentPollKey !== id) { state.paymentPollKey = id; state.paymentPollCount = 0; }
  state.needsPaymentPoll = pending;
  const retry = order.status === 'NEW' && order.paymentStatus === 'FAILED' && new Date(order.paymentExpires) > new Date();
  return page(title, '', `<section class="panel auth-card" aria-live="polite"><h2 class="h4">${esc(order.code)}</h2><p>${esc(message)}</p>
    <div class="summary-row"><span>Số tiền</span><strong>${money(order.total)}</strong></div>
    <p class="mt-3">${badge(order.paymentStatus)}</p>${order.transactionRef ? `<p class="small">Mã giao dịch: ${esc(order.transactionRef)}</p>` : ''}
    <div class="inline-actions">${retry ? `<button class="btn btn-primary" data-action="pay" data-id="${order.id}">Thử thanh toán lại</button>` : ''}
    ${pending ? '<button class="btn btn-outline-primary" data-action="retry">Cập nhật trạng thái</button>' : ''}
    <a class="btn btn-outline-primary" href="#order/${order.id}">Chi tiết đơn</a><a class="small-link" href="#orders">Tất cả đơn hàng</a></div></section>`);
}

function schedulePaymentRefresh() {
  if (!state.needsPaymentPoll || (state.paymentPollCount || 0) >= 10) return;
  state.paymentPoll = setTimeout(() => {
    if (!location.hash.startsWith('#payment-result/')) return;
    state.paymentPollCount = (state.paymentPollCount || 0) + 1;
    render();
  }, 3000);
}
