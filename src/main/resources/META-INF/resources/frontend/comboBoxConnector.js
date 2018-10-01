window.Vaadin.Flow.comboBoxConnector = {
  initLazy: function (comboBox) {
    // Check whether the connector was already initialized for the ComboBox
    if (comboBox.$connector) {
      return;
    }

    comboBox.$connector = {};

    const pageCallbacks = {};
    const cache = {};

    comboBox.size = 0; // To avoid NaN here and there before we get proper data

    comboBox.dataProvider = function (params, callback) {

      if (params.pageSize != comboBox.pageSize) {
        throw 'Invalid pageSize';
      }

      console.log('CALLING DATAPROVIDER FOR PAGE ' + params.page);
      console.log(params);
      console.log('COMBOBOX.SIZE ' + comboBox.size);

      if (cache[params.page]) {
        // This may happen after skipping pages by scrolling fast
        console.log('FOUND FROM CACHE');
        let data = cache[params.page];
        delete cache[params.page];
        callback(data, comboBox.size);
      } else {
        const upperLimit = params.pageSize * (params.page + 1);
        console.log('RANGE UPPER LIMIT ' + upperLimit);
        comboBox.$server.setRequestedRange(0, upperLimit);

        pageCallbacks[params.page] = callback;
      }
    }

    comboBox.$connector.set = function (index, items) {
      if (index % comboBox.pageSize != 0) {
        throw 'Got new data to index ' + index + ' which is not aligned with the page size of ' + comboBox.pageSize;
      }

      console.log('SETTING ITEMS');
      console.log('index ' + index);
      console.log(items);
      console.log('COMBOBOX.SIZE ' + comboBox.size);

      const firstPage = index / comboBox.pageSize;
      const updatedPageCount = Math.ceil(items.length / comboBox.pageSize);

      for (let i = 0; i < updatedPageCount; i++) {
        let page = firstPage + i;
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
      console.log('SETTING COMBOBOX.SIZE ' + newSize);
      comboBox.size = newSize;
    };

    comboBox.$connector.confirm = function (id) {
      // We're done applying changes from this batch, resolve outstanding
      // callbacks
      let outstandingRequests = Object.getOwnPropertyNames(pageCallbacks);
      for (let i = 0; i < outstandingRequests.length; i++) {
        let page = outstandingRequests[i];

        if (cache[page]) {
          let callback = pageCallbacks[page];
          delete pageCallbacks[page];

          let data = cache[page];
          delete cache[page];

          callback(data, comboBox.size);
        }
      }

      // Let server know we're done
      comboBox.$server.confirmUpdate(id);
    }
  }
}
