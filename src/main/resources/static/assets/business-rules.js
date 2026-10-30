'use strict';
function recipeConfigurationFields(p){
 const labels={PROTEIN:'Nhân',RICE:'Cơm',BEANS:'Đậu',SAUCE:'Sốt',TOPPING:'Topping',EXTRA:'Món thêm'},required=menuRequiredGroups(p),optional=String(p.optionalGroups??'TOPPING,EXTRA').split(','),suggested=String(p.suggestedIngredientIds||'').split(',').map(Number);
 const choices=(kind,selected,keys)=>`<div class="recipe-options">${keys.map(g=>`<label><input type="checkbox" data-recipe-${kind}="${g}" ${selected.includes(g)?'checked':''}> ${labels[g]}</label>`).join('')}</div>`;
 return `<fieldset class="recipe-fields"><legend>Quy tắc tự chọn nguyên liệu</legend><input type="hidden" name="requiredGroups" value="${esc(required.join(','))}"><input type="hidden" name="optionalGroups" value="${esc(optional.join(','))}"><input type="hidden" name="suggestedIngredientIds" value="${esc(p.suggestedIngredientIds||'')}"><p class="small muted">Quy tắc này giữ nguyên khi đổi tên món hoặc danh mục.</p><strong>Nhóm bắt buộc — mỗi nhóm chọn một</strong>${choices('required',required,['PROTEIN','RICE','BEANS','SAUCE'])}<strong>Nhóm tùy chọn</strong>${choices('optional',optional,Object.keys(labels))}<div class="form-grid mt-3">${field('ingredientCalorieFactor','Hệ số calories nguyên liệu',p.ingredientCalorieFactor??1,'number')}${select('vegetarian','Món chay',[['true','Có'],['false','Không']],String(p.vegetarian??false))}${select('includedProteinId','Nhân đã bao gồm trong giá',[['','Không bao gồm'],...state.data.ingredients.filter(i=>i.groupName==='PROTEIN').map(i=>[i.id,i.name]),...(p.includedProteinId&&!state.data.ingredients.some(i=>i.id===p.includedProteinId)?[[p.includedProteinId,'Nhân đã ngừng bán — giữ cách tính giá']]:[])],p.includedProteinId??'',false)}</div><strong>Nguyên liệu chọn sẵn khi mở món</strong><div class="form-grid">${Object.keys(labels).map(g=>`<label class="form-label">${labels[g]}<select class="form-select" data-recipe-suggestion="${g}"><option value="">${required.includes(g)?'Chọn nguyên liệu khả dụng đầu tiên':'Không chọn sẵn'}</option>${state.data.ingredients.filter(i=>i.groupName===g).map(i=>`<option value="${i.id}" ${suggested.includes(i.id)?'selected':''}>${esc(i.name)}</option>`).join('')}</select></label>`).join('')}</div></fieldset>`;
}
function bindBusinessRecipeForm(form){
 if(!form?.elements.requiredGroups)return;
 const sync=(event)=>{
  const changed=event?.target;if(changed?.dataset.recipeRequired&&changed.checked){const optional=form.querySelector(`[data-recipe-optional="${changed.dataset.recipeRequired}"]`);if(optional)optional.checked=false;}
  const required=[...form.querySelectorAll('[data-recipe-required]:checked')].map(i=>i.dataset.recipeRequired);
  form.querySelectorAll('[data-recipe-optional]').forEach(i=>{i.disabled=required.includes(i.dataset.recipeOptional);if(i.disabled)i.checked=false;});
  const optional=[...form.querySelectorAll('[data-recipe-optional]:checked')].map(i=>i.dataset.recipeOptional),allowed=[...required,...optional],ids=[];
  form.querySelectorAll('[data-recipe-suggestion]').forEach(select=>{select.disabled=!allowed.includes(select.dataset.recipeSuggestion);if(!select.disabled&&select.value)ids.push(select.value);});
  form.elements.requiredGroups.value=required.join(',');form.elements.optionalGroups.value=optional.join(',');form.elements.suggestedIngredientIds.value=ids.join(',');
 };form.addEventListener('change',sync);sync();
}
function bindBusinessSettingsForm(form){
 if(state.adminTab!=='settings'||!form?.elements.settingValue)return;
 const key=form.elements.name,value=form.elements.settingValue;
 const sync=()=>{const d=state.data.settingDefinitions.find(d=>d.name===key.value);if(!d)return;value.type=d.numeric?'number':'text';if(d.numeric){value.min=d.minimum;value.max=d.maximum;value.step='1';}else{value.removeAttribute('min');value.removeAttribute('max');value.maxLength=80;}};
 key.addEventListener('change',sync);sync();
}
