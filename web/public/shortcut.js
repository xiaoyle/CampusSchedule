// 在快捷指令「在网页上运行 JavaScript」操作中使用全部内容。
// 该脚本只在中大教务课表页面运行，不读取登录表单、Cookie、姓名或学号。
(async function () {
  'use strict';
  const finish = value => completion(JSON.stringify(value));
  const error = message => finish({ error: message });
  try {
    const hashRoute = location.hash.split('?')[0];
    if (location.origin !== 'https://jwxt.sysu.edu.cn' || location.pathname !== '/jwxt/mk/schedule-web/' || hashRoute !== '#/studentTimeTabPrint')
      return error('请先在 Safari 进入本科教务系统的“课表查询”页面');

    // 电脑版若已经展示完整课表，直接读取表格；不接触搜索表单和身份信息。
    const selectedAll = Array.from(document.querySelectorAll('[role="tab"][aria-selected="true"],.ant-tabs-tab-active'))
      .some(node => node.textContent.trim() === '全部');
    const table = document.querySelector('#table-bot table');
    if (selectedAll && table && table.rows.length > 1 && !document.querySelector('.ant-spin-spinning')) {
      const rows = Array.from(table.rows).map(row => Array.from(row.cells).map(cell => ({
        text: cell.textContent.trim(), rowSpan: cell.rowSpan, colSpan: cell.colSpan
      })));
      const count = rows.reduce((sum, row) => sum + row.length, 0);
      if (count > 6000 || rows.some(row => row.some(cell => cell.text.length > 12000)))
        return error('课表数据过大，请改用 Word、PDF 或图片导入');
      const title = Array.from(document.querySelectorAll('h1,h2,h3'))
        .map(node => (node.textContent.match(/\d{4}学年度第[一二三四1234]学期/) || [])[0])
        .find(Boolean) || '导入学期';
      return finish({ format: 'campus-schedule', version: 1, snapshot: { title, rows, allWeeks: true } });
    }

    // iPhone 的移动课表没有“全部”按钮。读取当前学期，并在同一登录会话内
    // 调用学校课表页自身使用的查询接口，请求 week=99（完整学期）。
    const visible = document.body ? (document.body.innerText || document.body.textContent || '') : '';
    const termMatch = visible.match(/(20\d{2})\s*[-－]\s*([1-4])\s*学期/) ||
      visible.match(/(20\d{2})\s*学年度\s*第([一二三四1234])\s*学期/);
    if (!termMatch) return error('没有识别到当前学期，请等待课表加载完成后再运行');
    const numberMap = { 一: '1', 二: '2', 三: '3', 四: '4' };
    const termNumber = numberMap[termMatch[2]] || termMatch[2];
    const acadYear = termMatch[1] + '-' + termNumber;

    const request = await fetch('/jwxt/timetable-search/stuTimeTabPrint/studentQuery', {
      method: 'POST', credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json;charset=UTF-8' },
      body: JSON.stringify({ acadYear, submitFlag: '1', week: '99', nothroughCourseFlag: '1' })
    });
    if (!request.ok) return error('学校课表请求失败（' + request.status + '），请重新登录后再试');
    const response = await request.json();
    if (!response || response.code !== 200 || !response.data || !response.data.timetable)
      return error('学校没有返回完整课表，请确认已进入课表查询页面');

    const defaultTimes = [
      ['08:00','08:45'], ['08:55','09:40'], ['10:10','10:55'], ['11:05','11:50'],
      ['14:20','15:05'], ['15:15','16:00'], ['16:30','17:15'], ['17:25','18:10'],
      ['19:00','19:45'], ['19:55','20:40'], ['20:50','21:35']
    ];
    let periodRows = [];
    try {
      const timeRequest = await fetch('/jwxt/base-info/AcadyeartermSet/minorName?schoolYear=' + encodeURIComponent(acadYear), { credentials: 'same-origin' });
      const timeResponse = await timeRequest.json();
      if (timeResponse && timeResponse.code === 200 && Array.isArray(timeResponse.data)) periodRows = timeResponse.data;
    } catch (_) { /* 课表仍可使用学校常规节次作为兜底 */ }

    const clean = value => String(value == null ? '' : value).replace(/^\s*\/+|\/+\s*$/g, '').trim();
    const expandWeeks = value => {
      const normalized = clean(value).replace(/[，、]/g, ',').replace(/[－～]/g, '-');
      const match = normalized.match(/(\d+(?:-\d+)?(?:,\d+(?:-\d+)?)*)\s*(?:周)?\s*(每周|单周|双周|单|双)?/);
      if (!match) return [];
      const odd = match[2] && match[2].startsWith('单');
      const even = match[2] && match[2].startsWith('双');
      const result = [];
      match[1].split(',').forEach(part => {
        const range = part.split('-').map(Number), start = range[0], end = range[1] || range[0];
        for (let week = start; week <= end && week <= 60; week++)
          if ((!odd && !even) || (odd && week % 2 === 1) || (even && week % 2 === 0)) result.push(week);
      });
      return Array.from(new Set(result)).sort((a, b) => a - b);
    };
    const groups = new Map();
    Object.keys(response.data.timetable).forEach(key => {
      const match = String(key).match(/^([1-7])(\d{1,2})$/);
      if (!match) return;
      const weekday = Number(match[1]), startPeriod = Number(match[2]);
      const cells = response.data.timetable[key];
      if (!Array.isArray(cells)) return;
      cells.forEach(cell => {
        if (!cell || cell.emptyFlag) return;
        const weekNumbers = expandWeeks(cell.timeDetail);
        const title = clean(cell.courseName).replace(/^本\s*[（(][^）)]*[）)]\s*/, '');
        if (!weekNumbers.length || !title || startPeriod < 1 || startPeriod > 40) return;
        const endPeriod = Math.min(40, startPeriod + Math.max(1, Number(cell.rowSpan) || 1) - 1);
        const groupKey = [weekday, startPeriod, endPeriod, title.replace(/\s/g, '')].join('|');
        const group = groups.get(groupKey) || { weekday, startPeriod, endPeriod, title, variants: new Map() };
        const variantKey = weekNumbers.join(',');
        const candidates = group.variants.get(variantKey) || [];
        const candidate = { teacher: clean(cell.teachingStaffName), location: clean(cell.classPlace) };
        if (!candidates.some(item => item.teacher === candidate.teacher && item.location === candidate.location)) candidates.push(candidate);
        group.variants.set(variantKey, candidates);
        groups.set(groupKey, group);
      });
    });
    const rules = Array.from(groups.values()).map((group, index) => ({
      id: ['web', acadYear, group.weekday, group.startPeriod, group.endPeriod, index].join('@'),
      title: group.title, weekday: group.weekday, startPeriod: group.startPeriod, endPeriod: group.endPeriod,
      variants: Array.from(group.variants.entries()).map(([key, candidates]) => ({ weeks: key.split(',').map(Number), candidates }))
    }));
    if (!rules.length) return error('完整课表中没有识别到课程，请确认当前学期有课');
    const maxPeriod = Math.max.apply(null, rules.map(rule => rule.endPeriod));
    const periodMap = new Map(periodRows.map(row => [Number(row.sectionNumber), row]));
    const periods = Array.from({ length: maxPeriod }, (_, index) => {
      const number = index + 1, row = periodMap.get(number), fallback = defaultTimes[index] || ['00:00','00:01'];
      return { number, start: row && row.startTime || fallback[0], end: row && row.endTime || fallback[1] };
    });
    const cn = ['零','一','二','三','四'][Number(termNumber)] || termNumber;
    return finish({ format: 'campus-schedule', version: 1, schedule: {
      term: termMatch[1] + '学年度第' + cn + '学期', firstMonday: '', periods, rules
    }});
  } catch (reason) {
    const detail = reason && reason.name === 'SyntaxError' ? '学校返回的数据格式发生变化' : '课表读取失败';
    return error(detail + '，请重新加载课表后再试；仍失败可改用 Word、PDF 或图片导入');
  }
})();
