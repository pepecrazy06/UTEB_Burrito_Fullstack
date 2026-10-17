'use strict';

function registrationPage() {
  const today = new Date();
  const maxDate = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
  return `<section class="registration-page" aria-labelledby="registration-title">
    <div class="registration-content">
      <img class="auth-page-logo" src="/assets/uteb-logo.png" alt="Logo UTEB" width="112" height="112">
      <h1 id="registration-title">Tạo tài khoản</h1>
      <p class="registration-intro">Hãy tạo tài khoản để nhận phần thưởng và<br class="registration-desktop-break"> đặt món yêu thích nhanh hơn.</p>
      <form id="register-form" class="registration-form">
        <div class="registration-name-grid">
        <div class="registration-field">
          <label for="register-first-name">Tên</label>
          <input id="register-first-name" name="firstName" autocomplete="given-name" maxlength="98" placeholder=" " required>
        </div>
        <div class="registration-field">
          <label for="register-last-name">Họ</label>
          <input id="register-last-name" name="lastName" autocomplete="family-name" maxlength="98" placeholder=" " required>
        </div>
        </div>
        <div class="registration-field">
          <label for="register-email">E-mail</label>
          <input id="register-email" name="email" type="email" autocomplete="email" maxlength="160" placeholder=" " required>
        </div>
        <div class="registration-field registration-password-field">
          <label for="register-password">Mật khẩu</label>
          <div class="registration-password-input">
            <input id="register-password" name="password" type="password" autocomplete="new-password" minlength="8" maxlength="72" aria-describedby="register-password-help" placeholder=" " required>
            <button class="registration-password-toggle" type="button" aria-label="Hiện mật khẩu" aria-controls="register-password" aria-pressed="false">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z"/><circle cx="12" cy="12" r="3.5"/><path class="registration-eye-slash" d="m4 3 16 18"/></svg>
            </button>
          </div>
          <div id="register-password-help" class="registration-hint registration-password-help">
            <p>Gợi ý để mật khẩu mạnh hơn:</p>
            <ul class="registration-password-rules" aria-label="Độ mạnh mật khẩu">
              <li data-password-rule="upper"><span aria-hidden="true">ABC</span><span class="visually-hidden">Chữ hoa</span></li>
              <li data-password-rule="lower"><span aria-hidden="true">abc</span><span class="visually-hidden">Chữ thường</span></li>
              <li data-password-rule="number"><span aria-hidden="true">123</span><span class="visually-hidden">Chữ số</span></li>
              <li data-password-rule="special"><span aria-hidden="true">!@%</span><span class="visually-hidden">Ký tự đặc biệt</span></li>
              <li data-password-rule="length"><span>8 ký tự</span></li>
            </ul>
            <span class="registration-password-status visually-hidden" role="status"></span>
          </div>
        </div>
        <div class="registration-field registration-phone-field">
          <label for="register-phone">Số điện thoại di động</label>
          <input id="register-phone" name="phone" type="tel" autocomplete="tel" inputmode="tel" maxlength="20" aria-describedby="register-phone-help" placeholder=" " required>
          <p id="register-phone-help" class="registration-hint">Vui lòng nhập số điện thoại di động hợp lệ để cửa hàng liên hệ về đơn hàng. Mã xác thực tài khoản sẽ được gửi qua email.</p>
        </div>
        <div class="registration-field registration-birthday-field">
          <label for="register-birth-date">Ngày sinh</label>
          <input id="register-birth-date" name="birthDate" type="date" autocomplete="bday" min="1900-01-01" max="${maxDate}" aria-describedby="register-birthday-help" placeholder=" " required>
          <p id="register-birthday-help" class="registration-hint">Hãy kiểm tra kỹ ngày sinh trước khi đăng ký. Bạn sẽ không thể thay đổi ngày sinh trong hồ sơ sau này.</p>
        </div>
        <p id="register-error" class="registration-error" role="alert" hidden></p>
        <button type="submit" class="btn btn-primary registration-submit">Tạo tài khoản</button>
        <p class="registration-login">Đã có tài khoản? <a href="#login">Đăng nhập</a></p>
      </form>
    </div>
  </section>`;
}

function loginPage() {
  return `<section class="registration-page auth-login-page" aria-labelledby="login-title">
    <div class="registration-content auth-login-content">
      <img class="auth-page-logo" src="/assets/uteb-logo.png" alt="Logo UTEB" width="112" height="112">
      <h1 id="login-title">Đăng nhập</h1>
      <p class="registration-intro">Bữa ngon tiếp theo đang chờ bạn.</p>
      <form id="login-form" class="registration-form">
        <div class="registration-field">
          <label for="login-email">E-mail</label>
          <input id="login-email" name="email" type="email" autocomplete="username" maxlength="160" placeholder=" " required>
        </div>
        <div class="registration-field registration-password-field">
          <label for="login-password">Mật khẩu</label>
          <div class="registration-password-input">
            <input id="login-password" name="password" type="password" autocomplete="current-password" placeholder=" " required>
            <button class="registration-password-toggle" type="button" aria-label="Hiện mật khẩu" aria-controls="login-password" aria-pressed="false">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z"/><circle cx="12" cy="12" r="3.5"/><path class="registration-eye-slash" d="m4 3 16 18"/></svg>
            </button>
          </div>
        </div>
        <div class="auth-forgot-link"><a href="#forgot">Quên mật khẩu?</a></div>
        <p id="login-error" class="registration-error" role="alert" hidden></p>
        <button type="submit" class="btn btn-primary registration-submit">Đăng nhập</button>
        <p class="registration-login">Chưa có tài khoản? <a href="#register">Tạo tài khoản</a></p>
      </form>
      ${state.data.demo ? `<details class="auth-demo-accounts"><summary>Tài khoản dùng thử</summary><p>Mật khẩu: <strong>Uteb@12345</strong></p><div class="inline-actions">${['customer','staff','manager','admin','vendor','shipper'].map(role => `<button type="button" class="btn btn-sm btn-outline-primary" data-action="demo-login" data-value="${role}">${role}</button>`).join('')}</div></details>` : ''}
    </div>
  </section>`;
}

async function submitLogin(form, body) {
  const error = form.querySelector('#login-error');
  error.hidden = true;
  try {
    await login(body.email, body.password);
  } catch (err) {
    error.textContent = err.message;
    error.hidden = false;
    throw err;
  }
}

function updateRegistrationValidity(form) {
  const firstName = form.elements.firstName;
  const lastName = form.elements.lastName;
  const name = `${lastName.value.trim()} ${firstName.value.trim()}`;
  firstName.setCustomValidity(!firstName.value.trim() ? 'Vui lòng nhập tên.' : name.length > 100 ? 'Họ và tên không được vượt quá 100 ký tự.' : '');
  lastName.setCustomValidity(!lastName.value.trim() ? 'Vui lòng nhập họ.' : '');
  const phone = form.elements.phone;
  phone.setCustomValidity(phone.value && !/^[+0-9 ()-]{8,20}$/.test(phone.value.trim()) ? 'Vui lòng nhập số điện thoại hợp lệ (8–20 ký tự).' : '');
  const password = form.elements.password;
  const value = password.value;
  password.setCustomValidity(new TextEncoder().encode(value).length > 72 ? 'Mật khẩu không được vượt quá 72 byte UTF-8.' : value && value !== value.trim() ? 'Mật khẩu không được bắt đầu hoặc kết thúc bằng khoảng trắng.' : '');
  const rules = {upper: /[A-Z]/.test(value), lower: /[a-z]/.test(value), number: /[0-9]/.test(value), special: /[^\p{L}\p{N}\s]/u.test(value), length: value.length >= 8};
  for (const [rule, valid] of Object.entries(rules)) {
    const item = form.querySelector(`[data-password-rule="${rule}"]`);
    item.classList.toggle('is-met', valid);
    item.querySelector('.registration-rule-result')?.remove();
    const result = document.createElement('span');
    result.className = 'visually-hidden registration-rule-result';
    result.textContent = valid ? ': đạt' : ': chưa đạt';
    item.append(result);
  }
  form.querySelector('.registration-password-status').textContent = value ? `Đáp ứng ${Object.values(rules).filter(Boolean).length} trên 5 gợi ý mật khẩu.` : '';
}

async function submitRegistration(form, body) {
  updateRegistrationValidity(form);
  if (!form.reportValidity()) return;
  const error = form.querySelector('#register-error');
  error.hidden = true;
  const payload = {name: `${body.lastName.trim()} ${body.firstName.trim()}`.replace(/\s+/g, ' '), email: body.email.trim(), phone: body.phone.trim(), password: body.password, birthDate: body.birthDate};
  try {
    await api('/auth/register', 'POST', payload);
    state.registrationEmail = payload.email;
    toast('Đã gửi mã OTP. Vui lòng xác thực tài khoản.');
    location.hash = '#verify';
  } catch (err) {
    error.textContent = err.message;
    error.hidden = false;
    error.scrollIntoView({block: 'nearest'});
    throw err;
  }
}

document.addEventListener('input', event => {
  const form = event.target.closest('#register-form');
  if (form) updateRegistrationValidity(form);
});

document.addEventListener('click', event => {
  const toggle = event.target.closest('.registration-password-toggle');
  if (!toggle) return;
  const input = document.getElementById(toggle.getAttribute('aria-controls'));
  if (!input) return;
  const visible = input.type === 'password';
  input.type = visible ? 'text' : 'password';
  toggle.setAttribute('aria-pressed', String(visible));
  toggle.setAttribute('aria-label', visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu');
});
