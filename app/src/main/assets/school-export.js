(function () {
  'use strict';
  if (location.origin !== 'https://jwxt.sysu.edu.cn' || location.hash.split('?')[0] !== '#/studentTimeTabPrint') return false;
  if (window.__campusCapture) window.__campusCapture.stop();
  const state = {data:null,failed:false,active:true};
  const create = URL.createObjectURL;
  const submit = HTMLFormElement.prototype.submit;
  const abort = new AbortController();
  const output = url => { try { const u=new URL(url,location.href); return u.origin===location.origin && u.pathname.endsWith('/timetable-search/stuTimeTabPrint/output'); } catch (_) {return false;} };
  async function boundedBlob(response) {
    if(Number(response.headers.get('content-length'))>8388608) throw Error();
    const reader=response.body.getReader(); const parts=[]; let size=0;
    try { while(true){ const part=await reader.read(); if(part.done)break;size+=part.value.length;if(size>8388608)throw Error();parts.push(part.value); } }
    finally { await reader.cancel().catch(()=>{}); }
    return new Blob(parts);
  }
  async function save(blob) {
    if (!state.active || blob.size>8388608) {state.failed=true;return;}
    const reader=new FileReader();reader.onload=()=>{if(state.active)state.data=String(reader.result).split(',')[1];};reader.onerror=()=>{state.failed=true;};reader.readAsDataURL(blob);
  }
  state.read = async function(url) {
    if (!state.active || !(url.startsWith('blob:'+location.origin+'/') || url.startsWith('data:') || output(url))) return;
    try { const r=await fetch(url,{credentials:'same-origin',redirect:'error',signal:abort.signal});if(!r.ok)throw Error();await save(await boundedBlob(r)); }
    catch (_) { if(state.active)state.failed=true; }
  };
  URL.createObjectURL=function(blob){const url=create.call(URL,blob);if(blob instanceof Blob)save(blob);return url;};
  HTMLFormElement.prototype.submit=function(){
    if (!state.active || !output(this.action)) return submit.call(this);
    fetch(this.action,{method:'POST',body:new URLSearchParams(new FormData(this)),credentials:'same-origin',redirect:'error',signal:abort.signal})
      .then(r=>{if(!r.ok)throw Error();return boundedBlob(r);}).then(save).catch(()=>{if(state.active)state.failed=true;});
  };
  const onClick=e=>{const a=e.target.closest && e.target.closest('a[href]');if(a && (a.href.startsWith('blob:'+location.origin+'/') || a.href.startsWith('data:') || output(a.href))){e.preventDefault();state.read(a.href);}};
  document.addEventListener('click',onClick,true);
  state.stop=()=>{state.active=false;abort.abort();URL.createObjectURL=create;HTMLFormElement.prototype.submit=submit;document.removeEventListener('click',onClick,true);state.data=null;};
  window.__campusCapture=state;
  const button=Array.from(document.querySelectorAll('button')).find(e=>e.textContent.trim()==='导出课表');
  if (!button || button.disabled) {state.failed=true;return false;}
  button.click();return true;
})()
