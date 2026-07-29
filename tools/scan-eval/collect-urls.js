/* ============================================================================
   STEP 1 of 2 — run this in the browser, on the Google Images results page.

   HOW:  press F12 -> click the "Console" tab -> paste this whole thing -> Enter.
         (If Chrome asks you to type "allow pasting" first, do that, then paste.)

   It scrolls the page to load more results, collects the ORIGINAL full-size
   image URL behind each thumbnail, and saves them to a file called
   floorplan-urls.txt in your Downloads folder.

   Why not just grab the thumbnails? Google's thumbnails are ~250px wide. We
   need to read the room names printed on the plan, and text that small is
   unreadable. The originals are the whole point.
   ========================================================================== */
(async () => {
  const found = new Map();

  const harvest = () => {
    // Every Google Images result links to /imgres?...&imgurl=<the real image>
    document.querySelectorAll('a[href*="imgurl="]').forEach((a) => {
      try {
        const u = new URL(a.href, location.origin);
        const src = u.searchParams.get('imgurl');
        if (!src || !/^https?:\/\//i.test(src)) return;
        const w = +(u.searchParams.get('imgw') || u.searchParams.get('w') || 0);
        const h = +(u.searchParams.get('imgh') || u.searchParams.get('h') || 0);
        if (!found.has(src)) found.set(src, w * h);
      } catch (e) { /* skip malformed */ }
    });
  };

  harvest();
  console.log('[floorplans] starting with', found.size);

  // scroll to pull in more results
  for (let i = 0; i < 15 && found.size < 90; i++) {
    window.scrollBy(0, window.innerHeight * 2);
    await new Promise((r) => setTimeout(r, 900));
    harvest();
    console.log('[floorplans] after scroll', i + 1, '->', found.size);
  }

  // biggest images first, so the 50 we keep are the most readable
  const urls = [...found.entries()].sort((a, b) => b[1] - a[1]).map(([u]) => u);

  if (!urls.length) {
    console.warn('[floorplans] found nothing. Make sure you are on the IMAGES tab of Google results.');
    return;
  }

  const blob = new Blob([urls.join('\n')], { type: 'text/plain' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = 'floorplan-urls.txt';
  document.body.appendChild(a);
  a.click();
  a.remove();

  console.log('%c[floorplans] DONE — ' + urls.length + ' urls saved to floorplan-urls.txt',
              'color:#7A9E7E;font-weight:bold');
})();
