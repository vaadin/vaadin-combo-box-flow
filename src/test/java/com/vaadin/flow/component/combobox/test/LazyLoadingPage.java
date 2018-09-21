/*
 * Copyright 2000-2017 Vaadin Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.flow.component.combobox.test;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.router.Route;

@Route("lazy-loading")
public class LazyLoadingPage extends Div {

    public LazyLoadingPage() {

        ComboBox<String> comboBox = new ComboBox<>();

        List<String> items = IntStream.range(0, 1000).mapToObj(i -> "Item " + i)
                .collect(Collectors.toList());
        ListDataProvider<String> dp = DataProvider.ofCollection(items);
        // dp.addFilter(item -> {
        // String filterString = comboBox.getFilterString();
        // if (filterString == null || filterString.isEmpty()) {
        // return true;
        // }
        // return item.startsWith(comboBox.getFilterString());
        // });

        CallbackDataProvider<String, String> callbackDp = new CallbackDataProvider<>(
                query -> {
                    int offset = query.getOffset();
                    int limit = query.getLimit();
                    System.out.println("-- dataprovider query --");
                    System.out.println("offset " + offset);
                    System.out.println("limit " + limit);
                    return IntStream.range(offset, offset + limit)
                            .mapToObj(index -> "Item " + index);
                }, query -> 30000);

        comboBox.setDataProvider(dp);
        // comboBox.setDataProvider(callbackDp);

        // comboBox.setRenderer(new ComponentRenderer<H1, String>(s -> new
        // H1(s)));

        // comboBox.getElement().setProperty("pageSize", 10);

        NativeButton getButton = new NativeButton("get value", e -> {
            add(new Label(comboBox.getValue()));
            // add(new Label(comboBox.getElement().getProperty("value")));
        });

        NativeButton setButton = new NativeButton("set value", e -> {
            // comboBox.setValue(dp.getItems().iterator().next());
            // comboBox.setValue("Item 5");
            comboBox.setValue(dp.getItems().iterator().next());
        });

        // add(comboBox, getButton, setButton);

        List<Person> people = IntStream.range(0, 987)
                .mapToObj(i -> new Person("Person " + i, i))
                .collect(Collectors.toList());
        ListDataProvider<Person> personDataProvider = new ListDataProvider<>(
                people);

        ComboBox<Person> comboBox2 = new ComboBox<>();
        comboBox2.setDataProvider(personDataProvider);

        add(comboBox2);
        add(new NativeButton("get value",
                e -> add(new Label(comboBox2.getValue().getName()))));
        add(new NativeButton("set value",
                e -> comboBox2.setValue(people.get(3))));

    }

    public static class Person implements Serializable {
        private String name;
        private final int born;

        public Person(String name, int born) {
            this.name = name;
            this.born = born;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getBorn() {
            return born;
        }

        @Override
        public String toString() {
            return name + "(" + born + ")";
        }
    }
}
