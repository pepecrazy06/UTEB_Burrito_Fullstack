const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const app=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/app.js'),'utf8');
function environment(){
 const ingredients=[{id:1,name:'Gà nướng',groupName:'PROTEIN',price:0,calories:180,available:true},
  {id:2,name:'Bò BBQ',groupName:'PROTEIN',price:15000,calories:220,available:true},
  {id:3,name:'Đậu hũ chay',groupName:'PROTEIN',price:0,calories:150,available:true},
  {id:4,name:'Thêm thịt',groupName:'EXTRA',price:25000,calories:180,available:true},
  {id:5,name:'Sốt cay',groupName:'SAUCE',price:0,calories:25,available:true}];
 const c=vm.createContext({state:{data:{ingredients}}});
 vm.runInContext(app.slice(app.indexOf('function calculateBuilder('),app.indexOf('function openBuilder(')),c);
 return {c,ingredients};
}
test('named beef recipe includes its protein price once and supports substitution',()=>{
 const {c}=environment();const p={name:'Burrito Bò BBQ',category:'Burrito',price:99000,calories:320,customizable:true};
 assert.equal(c.calculateBuilder(p,[2]).price,99000);
 assert.equal(c.calculateBuilder(p,[1]).price,84000);
});
test('recipe groups match tacos, salad and kids rather than requiring all burrito groups',()=>{
 const {c}=environment();
 assert.deepEqual(Array.from(c.menuRequiredGroups({category:'Tacos'})),['PROTEIN','SAUCE']);
 assert.deepEqual(Array.from(c.menuRequiredGroups({category:'Salad'})),['PROTEIN','BEANS','SAUCE']);
 assert.deepEqual(Array.from(c.menuRequiredGroups({category:'Kids'})),['PROTEIN','RICE','SAUCE']);
});
test('vegetarian recipes exclude meat and suggest tofu while spicy recipes suggest spicy sauce',()=>{
 const {c,ingredients}=environment(),p={name:'Salad Chay',category:'Salad'};
 assert.equal(c.menuIngredientAllowed(p,ingredients[0]),false);
 assert.equal(c.menuIngredientAllowed(p,ingredients[3]),false);
 assert.equal(c.suggestedMenuIngredient(p,'PROTEIN').name,'Đậu hũ chay');
 assert.equal(c.suggestedMenuIngredient({name:'Burrito Gà cay'},'SAUCE').name,'Sốt cay');
});
test('kids calories and fixed-portion burritos use their intended calculation',()=>{
 const {c}=environment();
 assert.equal(c.calculateBuilder({name:'Kids Gà nướng',category:'Kids',price:55000,calories:150,customizable:true},[1]).kcal,240);
 assert.equal(c.calculateBuilder({name:'Burrito Trứng phô mai',category:'Burrito',price:89000,calories:650,customizable:false},[]).price,89000);
});
