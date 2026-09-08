// 在快捷指令「在网页上运行 JavaScript」操作中使用全部内容。
try {
const snapshot = (function () {
  'use strict';
  if (location.origin !== 'https://jwxt.sysu.edu.cn' || location.pathname !== '/jwxt/mk/schedule-web/' || location.hash.split('?')[0] !== '#/studentTimeTabPrint') return {error:'请先进入本科教务系统的“课表查询”'};
  const all = Array.from(document.querySelectorAll('[role="tab"][aria-selected="true"],.ant-tabs-tab-active')).some(e => e.textContent.trim() === '全部');
  if (!all) return {error:'请在课表查询中选择“全部”周次后导入'};
  const table = document.querySelector('#table-bot table');
  if (!table || table.rows.length < 2 || document.querySelector('.ant-spin-spinning')) return {error:'课表正在加载或尚未显示，请稍后重试'};
  if (table.rows.length > 80) return {error:'课表过大，请使用文件导入'};
  // Only course cells; never the search form, student identity or login fields.
  const rows = Array.from(table.rows).map(row => Array.from(row.cells).map(cell => ({text:cell.textContent.trim(),rowSpan:cell.rowSpan,colSpan:cell.colSpan})));
  if (rows.reduce((n,r) => n+r.length,0)>6000 || rows.some(r=>r.some(c=>c.text.length>12000))) return {error:'课表过大，请使用文件导入'};
  const term = Array.from(document.querySelectorAll('h1,h2,h3')).map(e=>(e.textContent.match(/\d{4}学年度第[一二三123]学期/)||[])[0]).find(Boolean);
  return {title:term || '导入学期',rows:rows,allWeeks:true};
})();
if(snapshot.error) completion(JSON.stringify({error:snapshot.error}));
else completion(JSON.stringify({format:"campus-schedule",version:1,snapshot:snapshot}));
} catch (_) { completion(JSON.stringify({error:"课表读取失败，请打开完整课表并选择全部周次"})); }
