window.Vaadin.Flow.comboBoxConnector = {
  initLazy: function (comboBox) {
    // Check whether the connector was already initialized for the ComboBox
    if (comboBox.$connector) {
      return;
    }
    const pageCallbacks = {};
    const cache = {};

    comboBox.size = 0; // To avoid NaN here and there before we get proper data

    comboBox.$connector = {};

    comboBox.itemValuePath = 'key';

    comboBox.dataProvider = function (params, callback) {

      if (params.pageSize != comboBox.pageSize) {
        throw 'Invalid pageSize';
      }

      console.log('CALLING DATAPROVIDER FOR PAGE ' + params.page);
      console.log(params);
      console.log('COMBOBOX.SIZE ' + comboBox.size);

      const upperLimit = params.pageSize * (params.page + 1);
      console.log('RANGE UPPER LIMIT ' + upperLimit);
      comboBox.$server.setRequestedRange(0, upperLimit);

      pageCallbacks[params.page] = callback;
    }

    comboBox.$connector.setValue = function (key) {

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

      return;

      let pagesToUpdate = [];
      for (let i = 0; i < items.length; i++) {
        let cacheLocation = itemToCacheLocation(items[i].key);
        if (cacheLocation) {
          cache[cacheLocation.page][cacheLocation.index] = items[i];
          if (pagesToUpdate.indexOf(cacheLocation.page) === -1) {
            pagesToUpdate.push(cacheLocation.page);
          }
        }
      }
      // IE11 doesn't work with the transpiled version of the forEach.
      for (var i = 0; i < pagesToUpdate.length; i++) {
        let page = pagesToUpdate[i];
        updatecomboBoxCache(page);
      }
    };

    comboBox.$connector.clear = function (index, length) {
      return;
      if (Object.keys(cache).length === 0) {
        return;
      }
      if (index % comboBox.pageSize != 0) {
        throw 'Got cleared data for index ' + index + ' which is not aligned with the page size of ' + comboBox.pageSize;
      }

      let firstPage = index / comboBox.pageSize;
      let updatedPageCount = Math.ceil(length / comboBox.pageSize);

      for (let i = 0; i < updatedPageCount; i++) {
        let page = firstPage + i;
        let items = cache[page];
        for (let j = 0; j < items.length; j++) {
          let item = items[j];
          if (selectedKeys[item.key]) {
            comboBox.$connector.doDeselection(item);
          }
        }
        delete cache[page];
        updatecomboBoxCache(page);
      }
    };

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
        // Resolve if we have data or if we don't expect to get data
        if (cache[page] /*|| page < lastRequestedRange[0] || page > lastRequestedRange[1]*/) {
          let callback = pageCallbacks[page];
          delete pageCallbacks[page];

          let data = cache[page];
          delete cache[page];

          callback(data, comboBox.size);

          // callback(cache[page] || new Array(comboBox.pageSize));
        }
      }

      // Let server know we're done
      comboBox.$server.confirmUpdate(id);
    }
  }
}
