// Not using ES6 imports in this file yet because the connector in V14 must
// still work in Legacy bower projects. See: `ironListConnector-es6.js` for
// the Polymer3 approach.
window.Vaadin.Flow.Legacy = window.Vaadin.Flow.Legacy || {};

window.Vaadin.Flow.comboBoxConnector = {
  initLazy: function (comboBox) {

    // Check whether the connector was already initialized for the ComboBox
    if (comboBox.$connector) {
      return;
    }

    if (window.Polymer) {
        // Polymer2 approach.
        window.Vaadin.Flow.Legacy.Debouncer = window.Vaadin.Flow.Legacy.Debouncer || Polymer.Debouncer;
        window.Vaadin.Flow.Legacy.timeOut = window.Vaadin.Flow.Legacy.timeOut || Polymer.Async.timeOut;
    } else if (!window.Vaadin.Flow.Legacy.Debouncer) {
        console.log("ComboBox is unable to load Polymer helpers.");
        return;
    }

    const Debouncer = window.Vaadin.Flow.Legacy.Debouncer;
    const timeOut = window.Vaadin.Flow.Legacy.timeOut;

    comboBox.$connector = {};

    // holds pageIndex -> callback pairs of subsequent indexes (current active range)
    let pageCallbacks = {};
    let cache = {};
    let lastFilter = '';
    const placeHolder = new Vaadin.ComboBoxPlaceholder();
    const MAX_RANGE_COUNT = Math.max(comboBox.pageSize * 2, 150); // Max item count in active range

    // From https://codeburst.io/throttling-and-debouncing-in-javascript-b01cad5c8edf
    const throttle = (func, limit) => {
      let lastFunc
      let lastRan
      return function() {
        const context = this
        const args = arguments
        if (!lastRan) {
          func.apply(context, args)
          lastRan = Date.now()
        } else {
          clearTimeout(lastFunc)
          lastFunc = setTimeout(function() {
            if ((Date.now() - lastRan) >= limit) {
              func.apply(context, args)
              lastRan = Date.now()
            }
          }, limit - (Date.now() - lastRan))
        }
      }
    };
    let setRequestedRange;
    const rangeRequestToggle = throttle(() => setRequestedRange && setRequestedRange(), 50);

    const clearPageCallbacks = (pages = Object.keys(pageCallbacks)) => {
      // Flush and empty the existing requests
      pages.forEach(page => {
        pageCallbacks[page]([], comboBox.size);
        delete pageCallbacks[page];

        // Empty the comboBox's internal cache without invoking observers by filling
        // the filteredItems array with placeholders (comboBox will request for data when it
        // encounters a placeholder)
        const pageStart = parseInt(page) * comboBox.pageSize;
        const pageEnd = pageStart + comboBox.pageSize;
        for (let i = 0; i < comboBox.filteredItems.length; i++) {
          if (i >= pageStart && i < pageEnd) {
            comboBox.filteredItems[i] = placeHolder;
          }
        }
      });
    }

    comboBox.dataProvider = function (params, callback) {
      if (params.pageSize != comboBox.pageSize) {
        throw 'Invalid pageSize';
      }

      if (comboBox._clientSideFilter) {
        // For clientside filter we first make sure we have all data which we also
        // filter based on comboBox.filter. While later we only filter clientside data.

        if (cache[0]) {
          performClientSideFilter(cache[0], callback)
          return;

        } else {
          // If client side filter is enabled then we need to first ask all data
          // and filter it on client side, otherwise next time when user will
          // input another filter, eg. continue to type, the local cache will be only
          // what was received for the first filter, which may not be the whole
          // data from server (keep in mind that client side filter is enabled only
          // when the items count does not exceed one page).
          params.filter = "";
        }
      }

      const filterChanged = params.filter !== lastFilter;
      if (filterChanged) {
        cache = {};
        lastFilter = params.filter;
        this._debouncer = Debouncer.debounce(
          this._debouncer,
          timeOut.after(500),
          () => {
            clearPageCallbacks();
            comboBox.clearCache();
            if (params.filter === '') {
              // Fixes the case when the filter changes
              // from '' to something else and back to ''
              // within debounce timeout, and the
              // DataCommunicator thinks it doesn't need to send data
              comboBox.$server.resetDataCommunicator();
            }
          });
        return;
      }

      if (cache[params.page]) {
        // This may happen after skipping pages by scrolling fast
        commitPage(params.page, callback);
      } else {
        pageCallbacks[params.page] = callback
        const activePages = Object.keys(pageCallbacks).map(page => parseInt(page));
        const rangeMin = Math.min(...activePages);
        const rangeMax = Math.max(...activePages);

        if (activePages.length * params.pageSize > MAX_RANGE_COUNT) {
          if (params.page === rangeMin) {
            clearPageCallbacks([String(rangeMax)]);
          } else {
            clearPageCallbacks([String(rangeMin)]);
          }
          comboBox.dataProvider(params, callback);
        } else if (rangeMax - rangeMin + 1 !== activePages.length) {
          // Wasn't a sequential page index, clear the cache so combo-box will request for new pages
          clearPageCallbacks();
        } else {
          // The requested page was sequential, extend the requested range
          const startIndex = params.pageSize * rangeMin;
          const endIndex = params.pageSize * (rangeMax + 1);
          const count = endIndex - startIndex;
          setRequestedRange = () => comboBox.$server.setRequestedRange(startIndex, count, params.filter);
          rangeRequestToggle();
        }
      }
    }

    comboBox.$connector.filter = function (item, filter) {
      filter = filter ? filter.toString().toLowerCase() : '';
      return comboBox._getItemLabel(item).toString().toLowerCase().indexOf(filter) > -1;
    }

    comboBox.$connector.set = function (index, items, filter) {
      if (filter != lastFilter) {
        return;
      }

      if (index % comboBox.pageSize != 0) {
        throw 'Got new data to index ' + index + ' which is not aligned with the page size of ' + comboBox.pageSize;
      }

      if (index === 0 && items.length === 0 && pageCallbacks[0]) {
        // Makes sure that the dataProvider callback is called even when server
        // returns empty data set (no items match the filter).
        cache[0] = [];
        return;
      }

      const firstPageToSet = index / comboBox.pageSize;
      const updatedPageCount = Math.ceil(items.length / comboBox.pageSize);

      for (let i = 0; i < updatedPageCount; i++) {
        let page = firstPageToSet + i;
        let slice = items.slice(i * comboBox.pageSize, (i + 1) * comboBox.pageSize);

        cache[page] = slice;
      }
    };

    comboBox.$connector.updateData = function (items) {
      // IE11 doesn't work with the transpiled version of the forEach.
      for (let i = 0; i < items.length; i++) {
        let item = items[i];

        for (let j = 0; j < comboBox.filteredItems.length; j++) {
          if (comboBox.filteredItems[j].key === item.key) {
            comboBox.set('filteredItems.' + j, item);
            break;
          }
        }
      }
    }

    comboBox.$connector.updateSize = function (newSize) {
      if (!comboBox._clientSideFilter) {
        // FIXME: It may be that this size set is unnecessary, since when
        // providing data to combobox via callback we may use data's size.
        // However, if this size reflect the whole data size, including
        // data not fetched yet into client side, and combobox expect it
        // to be set as such, the at least, we don't need it in case the
        // filter is clientSide only, since it'll increase the height of
        // the popup at only at first user filter to this size, while the
        // filtered items count are less.
        comboBox.size = newSize;
      }
    }

    comboBox.$connector.reset = function () {
      clearPageCallbacks();
      cache = {};
      comboBox.clearCache();
    }

    comboBox.$connector.confirm = function (id, filter) {

      if (filter != lastFilter) {
        return;
      }

      // We're done applying changes from this batch, resolve pending
      // callbacks
      let activePages = Object.getOwnPropertyNames(pageCallbacks);
      for (let i = 0; i < activePages.length; i++) {
        let page = activePages[i];

        if (cache[page]) {
          commitPage(page, pageCallbacks[page]);
        }
      }

      // Let server know we're done
      comboBox.$server.confirmUpdate(id);
    }

    comboBox.$connector.enableClientValidation = function( enable ){
        let input = null;
        if ( comboBox.$ ){
            input = comboBox.$["input"];
        }
        if (input){
            if ( enable){
                enableClientValidation(comboBox);
                enableTextFieldClientValidation(input);
            }
            else {
                disableClientValidation(comboBox);
                disableTextFieldClientValidation(input,comboBox );
            }
        }
        else {
            setTimeout( function(){
                comboBox.$connector.enableClientValidation(enable);
                }, 10);
        }
    }

    const disableClientValidation =  function (combo){
        if ( typeof combo.$checkValidity == 'undefined'){
            combo.$checkValidity = combo.checkValidity;
            combo.checkValidity = function() { return true; };
        }
        if ( typeof combo.$validate == 'undefined'){
            combo.$validate = combo.validate;
            combo.validate = function() { return true; };
        }
    }

    const disableTextFieldClientValidation =  function (field, comboBox){
        if ( typeof field.$checkValidity == 'undefined'){
            field.$checkValidity = field.checkValidity;
            field.checkValidity = function() { return !comboBox.invalid; };
        }
    }

    const enableTextFieldClientValidation = function (field){
        if ( field.$checkValidity ){
            field.checkValidity = field.$checkValidity;
            delete field.$checkValidity;
        }
     }

    const enableClientValidation = function (combo){
        if ( combo.$checkValidity ){
            combo.checkValidity = combo.$checkValidity;
            delete combo.$checkValidity;
        }
          if ( combo.$validate ){
            combo.validate = combo.$validate;
            delete combo.$validate;
        }
     }

    const commitPage = function (page, callback) {
      let data = cache[page];

      if (comboBox._clientSideFilter) {
        performClientSideFilter(data, callback)

      } else {
        // Remove the data if server-side filtering, but keep it for client-side
        // filtering
        delete cache[page];

        // FIXME: It may be that we ought to provide data.length instead of
        // comboBox.size and remove updateSize function.
        callback(data, comboBox.size);
      }
    }

    // Perform filter on client side (here) using the items from specified page
    // and submitting the filtered items to specified callback.
    // The filter used is the one from combobox, not the lastFilter stored since
    // that may not reflect user's input.
    const performClientSideFilter = function (page, callback) {

      let filteredItems = page;

      if (comboBox.filter) {
        filteredItems = page.filter(item =>
          comboBox.$connector.filter(item, comboBox.filter));
      }

      callback(filteredItems, filteredItems.length);
    }

    // https://github.com/vaadin/vaadin-combo-box-flow/issues/232
    comboBox.addEventListener('opened-changed', e =>
      e.detail.value && (comboBox.$.overlay._selector._manageFocus = () => {}));

    // Prevent setting the custom value as the 'value'-prop automatically
    comboBox.addEventListener('custom-value-set', e => e.preventDefault());
  }
}
