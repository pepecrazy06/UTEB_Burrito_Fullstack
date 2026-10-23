'use strict';

function productImageField(image = '/assets/food.png') {
  const current = image || '/assets/food.png';
  return `<div class="field product-image-field">
    <label for="product-image-file">Ảnh món ăn</label>
    <input type="hidden" name="image" value="${esc(current)}">
    <img class="product-image-preview" src="${esc(current)}" alt="Ảnh món ăn xem trước">
    <input id="product-image-file" name="productImage" type="file" class="form-control" accept="image/jpeg,image/png,image/webp" aria-describedby="product-image-help product-image-status">
    <small id="product-image-help" class="muted">Chọn ảnh JPG, PNG hoặc WebP từ máy, tối đa 10 MB. Ảnh được tải lên khi lưu. Không chọn ảnh mới sẽ giữ ảnh hiện tại.</small>
    <p id="product-image-status" class="small" role="status" aria-live="polite"></p>
  </div>`;
}

function validateProductImage(file) {
  requireClient(['image/jpeg', 'image/png', 'image/webp'].includes(file.type), 'Ảnh món ăn chỉ nhận JPG, PNG hoặc WebP.');
  requireClient(file.size > 0 && file.size <= 10 * 1024 * 1024, 'Vui lòng chọn ảnh có dung lượng lớn hơn 0 và không quá 10 MB.');
}

function bindProductImagePicker(form) {
  const input = form.querySelector('[name="productImage"]');
  if (!input) return;
  const preview = form.querySelector('.product-image-preview');
  const status = form.querySelector('#product-image-status');
  input.addEventListener('change', () => {
    input.uploadedImage = null;
    input.setCustomValidity('');
    status.textContent = '';
    status.classList.remove('error-message');
    preview.src = form.elements.image.value;
    const file = input.files[0];
    if (!file) return;
    try {
      validateProductImage(file);
      const reader = new FileReader();
      reader.onload = () => {
        if (input.files[0] === file) preview.src = reader.result;
      };
      reader.onerror = () => {
        if (input.files[0] !== file) return;
        input.setCustomValidity('Không đọc được ảnh. Vui lòng chọn lại.');
        status.textContent = input.validationMessage;
        status.classList.add('error-message');
      };
      reader.readAsDataURL(file);
      status.textContent = 'Đã chọn: ' + file.name;
    } catch (error) {
      input.setCustomValidity(error.message);
      status.textContent = error.message;
      status.classList.add('error-message');
    }
  });
}

async function prepareProductImage(form, body) {
  delete body.productImage;
  const input = form.querySelector('[name="productImage"]');
  const file = input?.files[0];
  if (!file) return;
  validateProductImage(file);
  const status = form.querySelector('#product-image-status');
  input.disabled = true;
  status.classList.remove('error-message');
  status.textContent = 'Đang tải ảnh lên…';
  try {
    // Reuse a successful upload if saving the product failed and the user retries.
    if (input.uploadedImage?.file !== file) {
      const data = new FormData();
      data.append('file', file);
      const media = await api('/media', 'POST', data);
      requireClient(media?.kind === 'image' && media.url?.startsWith('https://res.cloudinary.com/'), 'Máy chủ chưa trả về ảnh hợp lệ. Vui lòng thử lại.');
      input.uploadedImage = { file, url: media.url };
    }
    body.image = input.uploadedImage.url;
    form.elements.image.value = body.image;
    status.textContent = 'Ảnh đã tải lên. Đang lưu món ăn…';
  } catch (error) {
    status.textContent = error.message;
    status.classList.add('error-message');
    throw error;
  } finally {
    input.disabled = false;
  }
}
