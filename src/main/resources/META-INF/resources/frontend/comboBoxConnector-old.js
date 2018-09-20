window.Vaadin.Flow.comboBoxConnector = {
  initLazy: function(comboBox) {
    // Check whether the connector was already initialized for the ComboBox
    if (comboBox.$connector){
      return;
    }

    Vaadin.comboBox.ItemCache.prototype.ensureSubCacheForScaledIndex = function(scaledIndex) {
      if (!this.itemCaches[scaledIndex]) {

        if(ensureSubCacheDelay) {
          this.comboBox.$connector.beforeEnsureSubCacheForScaledIndex(this, scaledIndex);
        } else {
          this.doEnsureSubCacheForScaledIndex(scaledIndex);
        }
      }
    }

    Vaadin.comboBox.ItemCache.prototype.doEnsureSubCacheForScaledIndex = function(scaledIndex) {
      if (!this.itemCaches[scaledIndex]) {
        const subCache = new Vaadin.comboBox.ItemCache(this.comboBox, this, this.items[scaledIndex]);
        subCache.itemkeyCaches = {};
        if(!this.itemkeyCaches) {
          this.itemkeyCaches = {};
        }
        this.itemCaches[scaledIndex] = subCache;
        this.itemkeyCaches[this.comboBox.getItemId(subCache.parentItem)] = subCache;
        this.comboBox._loadPage(0, subCache);
      }
    }

    Vaadin.comboBox.ItemCache.prototype.getCacheAndIndexByKey = function(key) {
      for (let index in this.items) {
        if(comboBox.getItemId(this.items[index]) === key) {
          return {cache: this, scaledIndex: index};
        }
      }
      const keys = Object.keys(this.itemkeyCaches);
      for (let i = 0; i < keys.length; i++) {
        const expandedKey = keys[i];
        const subCache = this.itemkeyCaches[expandedKey];
        let cacheAndIndex = subCache.getCacheAndIndexByKey(key);
        if(cacheAndIndex) {
          return cacheAndIndex;
        }
      }
      return undefined;
    }

    Vaadin.comboBox.ItemCache.prototype.getLevel = function() {
      let cache = this;
      let level = 0;
      while (cache.parentCache) {
        cache = cache.parentCache;
        level++;
      }
      return level;
    }

    const rootPageCallbacks = {};
    const treePageCallbacks = {};
    const cache = {};

    /* ensureSubCacheDelay - true optimizes scrolling performance by adding small
    *  delay between each first page fetch of expanded item.
    *  Disable by setting to false.
    */
    const ensureSubCacheDelay = true;

    /* parentRequestDelay - optimizes parent requests by batching several requests
    *  into one request. Delay in milliseconds. Disable by setting to 0.
    *  parentRequestBatchMaxSize - maximum size of the batch.
    */
    const parentRequestDelay = 20;
    const parentRequestBatchMaxSize = 20;

    let parentRequestQueue = [];
    let parentRequestDebouncer;
    let ensureSubCacheQueue = [];
    let ensureSubCacheDebouncer;

    let lastRequestedRanges = {};
    const root = 'null';
    lastRequestedRanges[root] = [0, 0];

    const validSelectionModes = ['SINGLE', 'NONE', 'MULTI'];
    let selectedKeys = {};
    let selectionMode = 'SINGLE';

    let detailsVisibleOnClick = true;

    let sorterDirectionsSetFromServer = false;

    comboBox.size = 0; // To avoid NaN here and there before we get proper data
    comboBox.itemIdPath = 'key';

    comboBox.$connector = {};

    comboBox.$connector.hasEnsureSubCacheQueue = function() {
        return ensureSubCacheQueue.length > 0;
    }

    comboBox.$connector.hasParentRequestQueue = function() {
        return parentRequestQueue.length > 0;
    }

    comboBox.$connector.beforeEnsureSubCacheForScaledIndex = function(targetCache, scaledIndex) {
      // add call to queue
      ensureSubCacheQueue.push({
        cache: targetCache,
        scaledIndex: scaledIndex,
        itemkey: comboBox.getItemId(targetCache.items[scaledIndex]),
        level: targetCache.getLevel()
      });
      // sort by ascending scaledIndex and level
      ensureSubCacheQueue.sort(function(a, b) {
        return a.scaledIndex - b.scaledIndex || a.level - b.level;
      });
      if(!ensureSubCacheDebouncer) {
          comboBox.$connector.flushQueue(
            (debouncer) => ensureSubCacheDebouncer = debouncer,
            () => comboBox.$connector.hasEnsureSubCacheQueue(),
            () => comboBox.$connector.flushEnsureSubCache(),
            (action) => Polymer.Debouncer.debounce(ensureSubCacheDebouncer, Polymer.Async.animationFrame, action));
      }
    }

    comboBox.$connector.doSelection = function(item, userOriginated) {
      if (selectionMode === 'NONE') {
        return;
      }
      if (userOriginated && (comboBox.getAttribute('disabled') || comboBox.getAttribute('disabled') === '')) {
          return;
      }
      if (selectionMode === 'SINGLE') {
        comboBox.selectedItems = [];
        selectedKeys = {};
      }
      comboBox.selectItem(item);
      selectedKeys[item.key] = item;
      if (userOriginated) {
          item.selected = true;
          comboBox.$server.select(item.key);
      } else {
          comboBox.fire('select', {item: item, userOriginated: userOriginated});
      }

      if (selectionMode === 'MULTI' && arguments.length > 2) {
          for (i = 2; i < arguments.length; i++) {
              comboBox.$connector.doSelection(arguments[i], userOriginated);
          }
      }
    };

    comboBox.$connector.doDeselection = function(item, userOriginated) {
      if (selectionMode === 'SINGLE' || selectionMode === 'MULTI') {
        comboBox.deselectItem(item);
        delete selectedKeys[item.key];
        if (userOriginated) {
          delete item.selected;
          comboBox.$server.deselect(item.key);
        } else {
          comboBox.fire('deselect', {item: item, userOriginated: userOriginated});
        }
      }

      if (selectionMode === 'MULTI' && arguments.length > 2) {
          for (i = 2; i < arguments.length; i++) {
              comboBox.$connector.doDeselection(arguments[i], userOriginated);
          }
      }
    };

    comboBox.__activeItemChanged = function(newVal, oldVal) {
      if (selectionMode != 'SINGLE') {
        return;
      }
      if (!newVal) {
        if (oldVal && selectedKeys[oldVal.key]) {
          comboBox.$connector.doDeselection(oldVal, true);
        }
        return;
      }
      if (!selectedKeys[newVal.key]) {
        comboBox.$connector.doSelection(newVal, true);
      } else {
        comboBox.$connector.doDeselection(newVal, true);
      }
    };
    comboBox._createPropertyObserver('activeItem', '__activeItemChanged', true);

    comboBox.__activeItemChangedDetails = function(newVal, oldVal) {
      if(!detailsVisibleOnClick) {
        return;
      }
      if (newVal && !newVal.detailsOpened) {
        comboBox.$server.setDetailsVisible(newVal.key);
      } else {
        comboBox.$server.setDetailsVisible(null);
      }
    }
    comboBox._createPropertyObserver('activeItem', '__activeItemChangedDetails', true);

    comboBox.$connector.setDetailsVisibleOnClick = function(visibleOnClick) {
      detailsVisibleOnClick = visibleOnClick;
    };

    comboBox.$connector._getPageIfSameLevel = function(parentKey, index, defaultPage) {
      let cacheAndIndex = comboBox._cache.getCacheAndIndex(index);
      let parentItem = cacheAndIndex.cache.parentItem;
      let parentKeyOfIndex = (parentItem) ? comboBox.getItemId(parentItem) : root;
      if(parentKey !== parentKeyOfIndex) {
        return defaultPage;
      } else {
        return comboBox._getPageForIndex(cacheAndIndex.scaledIndex);
      }
    }

    comboBox.$connector.getCacheByKey = function(key) {
      let cacheAndIndex = comboBox._cache.getCacheAndIndexByKey(key);
      if(cacheAndIndex) {
        return cacheAndIndex.cache;
      }
      return undefined;
    }

    comboBox.$connector.flushQueue = function(timeoutIdSetter, hasQueue, flush, startTimeout) {
      if(!hasQueue()) {
        timeoutIdSetter(undefined);
        return;
      }
      if(flush()) {
          timeoutIdSetter(startTimeout(() =>
            comboBox.$connector.flushQueue(timeoutIdSetter, hasQueue, flush, startTimeout)));
      } else {
        comboBox.$connector.flushQueue(timeoutIdSetter, hasQueue, flush, startTimeout);
      }
    }

    comboBox.$connector.flushEnsureSubCache = function() {
      let fetched = false;
      let pendingFetch = ensureSubCacheQueue.splice(0, 1)[0];
      let itemkey =  pendingFetch.itemkey;

      let start = comboBox._virtualStart;
      let end = comboBox._virtualEnd;
      let buffer = end - start;
      let firstNeededIndex = Math.max(0, start + comboBox._vidxOffset - buffer);
      let lastNeededIndex = Math.min(end + comboBox._vidxOffset + buffer, comboBox._virtualCount);

      // only fetch if given item is still in visible range
      for(let index = firstNeededIndex; index <= lastNeededIndex; index++) {
        let item = comboBox._cache.getItemForIndex(index);

        if(comboBox.getItemId(item) === itemkey) {
          if(comboBox._isExpanded(item)) {
            pendingFetch.cache.doEnsureSubCacheForScaledIndex(pendingFetch.scaledIndex);
            return true;
          } else {
            break;
          }
        }
      }
      return false;
    }

    comboBox.$connector.flushParentRequests = function() {
      let pendingFetches = parentRequestQueue.splice(0, parentRequestBatchMaxSize);

      if(pendingFetches.length) {
          comboBox.$server.setParentRequestedRanges(pendingFetches);
          return true;
      }
      return false;
    }

    comboBox.$connector.beforeParentRequest = function(firstIndex, size, parentKey) {
      if(parentRequestDelay > 0) {
        // add request in queue
        parentRequestQueue.push({
          firstIndex: firstIndex,
          size: size,
          parentKey: parentKey
        });

        if(!parentRequestDebouncer) {
            comboBox.$connector.flushQueue(
              (debouncer) => parentRequestDebouncer = debouncer,
              () => comboBox.$connector.hasParentRequestQueue(),
              () => comboBox.$connector.flushParentRequests(),
              (action) => Polymer.Debouncer.debounce(parentRequestDebouncer, Polymer.Async.timeOut.after(parentRequestDelay), action)
              );
        }

      } else {
        comboBox.$server.setParentRequestedRange(firstIndex, size, parentKey);
      }
    }

    comboBox.$connector.fetchPage = function(fetch, page, parentKey) {
      // Determine what to fetch based on scroll position and not only
      // what comboBox asked for

      // The buffer size could be multiplied by some constant defined by the user,
      // if he needs to reduce the number of items sent to the comboBox to improve performance
      // or to increase it to make comboBox smoother when scrolling
      let start = comboBox._virtualStart;
      let end = comboBox._virtualEnd;
      let buffer = end - start;

      let firstNeededIndex = Math.max(0, start + comboBox._vidxOffset - buffer);
      let lastNeededIndex = Math.min(end + comboBox._vidxOffset + buffer, comboBox._virtualCount);

      let firstNeededPage = page;
      let lastNeededPage = page;
      for(let idx = firstNeededIndex; idx <= lastNeededIndex; idx++) {
        firstNeededPage = Math.min(firstNeededPage, comboBox.$connector._getPageIfSameLevel(parentKey, idx, firstNeededPage));
        lastNeededPage = Math.max(lastNeededPage, comboBox.$connector._getPageIfSameLevel(parentKey, idx, lastNeededPage));
      }

      let firstPage = Math.max(0,  firstNeededPage);
      let lastPage = (parentKey !== root) ? lastNeededPage: Math.min(lastNeededPage, Math.floor(comboBox.size / comboBox.pageSize));
      let lastRequestedRange = lastRequestedRanges[parentKey];
      if(!lastRequestedRange) {
        lastRequestedRange = [-1, -1];
      }
      if (lastRequestedRange[0] != firstPage || lastRequestedRange[1] != lastPage) {
        lastRequestedRange = [firstPage, lastPage];
        lastRequestedRanges[parentKey] = lastRequestedRange;
        let count = lastPage - firstPage + 1;
        fetch(firstPage * comboBox.pageSize, count * comboBox.pageSize);
      }
    }

    comboBox.dataProvider = function(params, callback) {
      if (params.pageSize != comboBox.pageSize) {
        throw 'Invalid pageSize';
      }

      let page = params.page;

      if(params.parentItem) {
        let parentUniqueKey = comboBox.getItemId(params.parentItem);
        if(!treePageCallbacks[parentUniqueKey]) {
          treePageCallbacks[parentUniqueKey] = {};
        }

        let parentCache = comboBox.$connector.getCacheByKey(parentUniqueKey);
        let itemCache = (parentCache) ? parentCache.itemkeyCaches[parentUniqueKey] : undefined;
        if(cache[parentUniqueKey] && cache[parentUniqueKey][page] && itemCache) {
          // workaround: sometimes comboBox-element gives page index that overflows
          page = Math.min(page, Math.floor(itemCache.size / comboBox.pageSize));

          callback(cache[parentUniqueKey][page], itemCache.size);
        } else {
          treePageCallbacks[parentUniqueKey][page] = callback;
        }
        comboBox.$connector.fetchPage((firstIndex, size) =>
            comboBox.$connector.beforeParentRequest(firstIndex, size, params.parentItem.key),
            page, parentUniqueKey);

      } else {
        // workaround: sometimes comboBox-element gives page index that overflows
        page = Math.min(page, Math.floor(comboBox.size / comboBox.pageSize));

        if (cache[root] && cache[root][page]) {
          callback(cache[root][page]);
        } else {
          rootPageCallbacks[page] = callback;
        }

        comboBox.$connector.fetchPage((firstIndex, size) => comboBox.$server.setRequestedRange(firstIndex, size), page, root);
      }
    }

    comboBox._updateItem = function(row, item) {
      Vaadin.comboBoxElement.prototype._updateItem.call(comboBox, row, item);

      // make sure that component renderers are updated
      Array.from(row.children).forEach(cell => {
        if(cell._instance && cell._instance.children) {
          Array.from(cell._instance.children).forEach(content => {
            if(content._attachRenderedComponentIfAble) {
              content._attachRenderedComponentIfAble();
            }
          });
        }
      });
    }

    comboBox._expandedInstanceChangedCallback = function(inst, value) {
      if (inst.item == undefined) {
        return;
      }
      let parentKey = comboBox.getItemId(inst.item);
      comboBox.$server.updateExpandedState(parentKey, value);
      if (value) {
        this.expandItem(inst.item);
      } else {
        delete cache[parentKey];
        let parentCache = comboBox.$connector.getCacheByKey(parentKey);
        if(parentCache && parentCache.itemkeyCaches[parentKey]) {
          parentCache.itemkeyCaches[parentKey].items = [];
        }
        delete lastRequestedRanges[parentKey];

        this.collapseItem(inst.item);
      }
    }

    const itemsUpdated = function(items) {
      if (!items || !Array.isArray(items)) {
        throw 'Attempted to call itemsUpdated with an invalid value: ' + JSON.stringify(items);
      }
      let detailsOpenedItems = Array.from(comboBox.detailsOpenedItems);
      let updatedSelectedItem = false;
      for (let i = 0; i < items.length; ++i) {
        const item = items[i];
        if(!item) {
          continue;
        }
        if (item.detailsOpened) {
          if(comboBox._getItemIndexInArray(item, detailsOpenedItems) < 0) {
            detailsOpenedItems.push(item);
          }
        } else if(comboBox._getItemIndexInArray(item, detailsOpenedItems) >= 0) {
          detailsOpenedItems.splice(comboBox._getItemIndexInArray(item, detailsOpenedItems), 1)
        }
        if (selectedKeys[item.key]) {
          selectedKeys[item.key] = item;
          item.selected = true;
          updatedSelectedItem = true;
        }
      }
      comboBox.detailsOpenedItems = detailsOpenedItems;
      if (updatedSelectedItem) {
        // IE 11 Object doesn't support method values
        comboBox.selectedItems = Object.keys(selectedKeys).map(function(e) {
          return selectedKeys[e]
        });
      }
    }

    const updatecomboBoxCache = function(page, parentKey) {
      if((parentKey || root) !== root) {
        const items = cache[parentKey][page];
        let parentCache = comboBox.$connector.getCacheByKey(parentKey);
        if(parentCache) {
          let _cache = parentCache.itemkeyCaches[parentKey];
          _updatecomboBoxCache(page, items,
            treePageCallbacks[parentKey][page],
            _cache);
        }

      } else {
        const items = cache[root][page];
        _updatecomboBoxCache(page, items, rootPageCallbacks[page], comboBox._cache);
      }
    }

    const _updatecomboBoxCache = function(page, items, callback, levelcache) {
      // Force update unless there's a callback waiting
      if(!callback) {
        let rangeStart = page * comboBox.pageSize;
        let rangeEnd = rangeStart + comboBox.pageSize;
        if (!items) {
          if(levelcache && levelcache.items) {
            for (let idx = rangeStart; idx < rangeEnd; idx++) {
              delete levelcache.items[idx];
            }
          }

        } else {
          if(levelcache && levelcache.items) {
            for (let idx = rangeStart; idx < rangeEnd; idx++) {
              if (levelcache.items[idx]) {
                levelcache.items[idx] = items[idx - rangeStart];
              }
            }
          }
          itemsUpdated(items);
        }
        /**
         * Calls the _assignModels function from comboBoxScrollerElement, that triggers
         * the internal revalidation of the items based on the _cache of the DataProviderMixin.
         */
        comboBox._assignModels();
      }
    }

    comboBox.$connector.set = function(index, items, parentKey) {
      if (index % comboBox.pageSize != 0) {
        throw 'Got new data to index ' + index + ' which is not aligned with the page size of ' + comboBox.pageSize;
      }
      let pkey = parentKey || root;

      const firstPage = index / comboBox.pageSize;
      const updatedPageCount = Math.ceil(items.length / comboBox.pageSize);

      for (let i = 0; i < updatedPageCount; i++) {
        let page = firstPage + i;
        let slice = items.slice(i * comboBox.pageSize, (i + 1) * comboBox.pageSize);
        if(!cache[pkey]) {
          cache[pkey] = {};
        }
        cache[pkey][page] = slice;
        for(let j = 0; j < slice.length; j++) {
          let item = slice[j]
          if (item.selected && !isSelectedOncomboBox(item)) {
            comboBox.$connector.doSelection(item);
          } else if (!item.selected && (selectedKeys[item.key] || isSelectedOncomboBox(item))) {
            comboBox.$connector.doDeselection(item);
          }
        }
        updatecomboBoxCache(page, pkey);
      }
    };

    const itemToCacheLocation = function(item) {
      let parent = item.parentUniqueKey || root;
      if(cache[parent]) {
        for (let page in cache[parent]) {
          for (let index in cache[parent][page]) {
            if (comboBox.getItemId(cache[parent][page][index]) === comboBox.getItemId(item)) {
              return {page: page, index: index, parentKey: parent};
            }
          }
        }
      }
      return null;
    }

    comboBox.$connector.updateData = function(items) {
      let pagesToUpdate = [];
      for (let i = 0; i < items.length; i++) {
        let cacheLocation = itemToCacheLocation(items[i]);
        if (cacheLocation) {
          cache[cacheLocation.parentKey][cacheLocation.page][cacheLocation.index] = items[i];
          let key = cacheLocation.parentKey+':'+cacheLocation.page;
          if (!pagesToUpdate[key]) {
            pagesToUpdate[key] = {parentKey: cacheLocation.parentKey, page: cacheLocation.page};
          }
        }
      }
      // IE11 doesn't work with the transpiled version of the forEach.
      let keys = Object.keys(pagesToUpdate);
      for (var i = 0; i < keys.length; i++) {
        let pageToUpdate = pagesToUpdate[keys[i]];
        updatecomboBoxCache(pageToUpdate.page, pageToUpdate.parentKey);
      }
    };

    comboBox.$connector.clearExpanded = function() {
      comboBox.expandedItems = [];
      ensureSubCacheQueue = [];
      parentRequestQueue = [];
    }

    comboBox.$connector.clear = function(index, length, parentKey) {
      let pkey = parentKey || root;
      if (!cache[pkey] || Object.keys(cache[pkey]).length === 0){
        return;
      }
      if (index % comboBox.pageSize != 0) {
        throw 'Got cleared data for index ' + index + ' which is not aligned with the page size of ' + comboBox.pageSize;
      }

      let firstPage = Math.floor(index / comboBox.pageSize);
      let updatedPageCount = Math.ceil(length / comboBox.pageSize);

      for (let i = 0; i < updatedPageCount; i++) {
        let page = firstPage + i;
        let items = cache[pkey][page];
        for (let j = 0; j < items.length; j++) {
          let item = items[j];
          if (selectedKeys[item.key]) {
            comboBox.$connector.doDeselection(item);
          }
        }
        delete cache[pkey][page];
        updatecomboBoxCache(page, parentKey);
      }
    };

    const isSelectedOncomboBox = function(item) {
      const selectedItems = comboBox.selectedItems;
      for(let i = 0; i < selectedItems; i++) {
        let selectedItem = selectedItems[i];
        if (selectedItem.key === item.key) {
          return true;
        }
      }
      return false;
    }

    comboBox.$connector.reset = function() {
      comboBox.size = 0;
      deleteObjectContents(cache);
      deleteObjectContents(comboBox._cache.items);
      deleteObjectContents(lastRequestedRanges);
      if(ensureSubCacheDebouncer) {
        ensureSubCacheDebouncer.cancel();
      }
      if(parentRequestDebouncer) {
        parentRequestDebouncer.cancel();
      }
      ensureSubCacheDebouncer = undefined;
      parentRequestDebouncer = undefined;
      ensureSubCacheQueue = [];
      parentRequestQueue = [];
      comboBox._assignModels();
    };

    const deleteObjectContents = function(obj) {
      let props = Object.keys(obj);
      for (let i = 0; i < props.length; i++) {
        delete obj[props[i]];
      }
    }

    comboBox.$connector.updateSize = function(newSize) {
      comboBox.size = newSize;
    };

    comboBox.$connector.updateUniqueItemIdPath = function(path) {
      comboBox.itemIdPath = path;
    }

    comboBox.$connector.expandItems = function(items) {
      let newExpandedItems = Array.from(comboBox.expandedItems);
      items.filter(item => !comboBox._isExpanded(item))
        .forEach(item =>
          newExpandedItems.push(item));
      comboBox.expandedItems = newExpandedItems;
    }

    comboBox.$connector.collapseItems = function(items) {
      let newExpandedItems = Array.from(comboBox.expandedItems);
      items.forEach(item => {
        let index = comboBox._getItemIndexInArray(item, newExpandedItems);
        if(index >= 0) {
            newExpandedItems.splice(index, 1);
        }
      });
      comboBox.expandedItems = newExpandedItems;
      items.forEach(item => comboBox.$connector.removeFromQueue(item));
    }

    comboBox.$connector.removeFromQueue = function(item) {
      let itemId = comboBox.getItemId(item);
      delete treePageCallbacks[itemId];
      comboBox.$connector.removeFromArray(ensureSubCacheQueue, item => item.itemkey === itemId);
      comboBox.$connector.removeFromArray(parentRequestQueue, item => item.parentKey === itemId);
    }

    comboBox.$connector.removeFromArray = function(array, removeTest) {
      if(array.length) {
        for(let index = array.length - 1; index--; ) {
           if (removeTest(array[index])) {
             array.splice(index, 1);
           }
        }
      }
    }

    comboBox.$connector.confirmParent = function(id, parentKey, levelSize) {
      if(!treePageCallbacks[parentKey]) {
        return;
      }
      let outstandingRequests = Object.getOwnPropertyNames(treePageCallbacks[parentKey]);
      for(let i = 0; i < outstandingRequests.length; i++) {
        let page = outstandingRequests[i];

        let lastRequestedRange = lastRequestedRanges[parentKey] || [0, 0];
        if((cache[parentKey] && cache[parentKey][page]) || page < lastRequestedRange[0] || page > lastRequestedRange[1]) {
          let callback = treePageCallbacks[parentKey][page];
          delete treePageCallbacks[parentKey][page];
          let items = cache[parentKey][page] || new Array(levelSize);
          callback(items, levelSize);
        }
      }
      // Let server know we're done
      comboBox.$server.confirmParentUpdate(id, parentKey);
    };

    comboBox.$connector.confirm = function(id) {
      // We're done applying changes from this batch, resolve outstanding
      // callbacks
      let outstandingRequests = Object.getOwnPropertyNames(rootPageCallbacks);
      for(let i = 0; i < outstandingRequests.length; i++) {
        let page = outstandingRequests[i];
        let lastRequestedRange = lastRequestedRanges[root] || [0, 0];
        // Resolve if we have data or if we don't expect to get data
        if ((cache[root] && cache[root][page]) || page < lastRequestedRange[0] || page > lastRequestedRange[1]) {
          let callback = rootPageCallbacks[page];
          delete rootPageCallbacks[page];
          callback(cache[root][page] || new Array(comboBox.pageSize));
        }
      }

      // Let server know we're done
      comboBox.$server.confirmUpdate(id);
    }

    comboBox.$connector.ensureHierarchy = function() {
      for (let parentKey in cache) {
        if(parentKey !== root) {
          delete cache[parentKey];
        }
      }
      deleteObjectContents(lastRequestedRanges);

      comboBox._cache.itemCaches = {};
      comboBox._cache.itemkeyCaches = {};

      comboBox._assignModels();
    }

    comboBox.$connector.setSelectionMode = function(mode) {
      if ((typeof mode === 'string' || mode instanceof String)
      && validSelectionModes.indexOf(mode) >= 0) {
        selectionMode = mode;
        selectedKeys = {};
      } else {
        throw 'Attempted to set an invalid selection mode';
      }
    }

  }
}
