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
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.router.Route;

@Route("lazy-loading")
public class LazyLoadingPage extends Div {

    private Div message = new Div();

    public LazyLoadingPage() {
        message.setId("message");
        add(message);

        createListDataProviderWithStrings();

        // List<Person> people = IntStream.range(0, 987)
        // .mapToObj(i -> new Person("Person " + i, i))
        // .collect(Collectors.toList());
        // ListDataProvider<Person> personDataProvider = new ListDataProvider<>(
        // people);
        //
        // ComboBox<Person> comboBox2 = new ComboBox<>();
        // comboBox2.setDataProvider(personDataProvider);
        //
        // add(comboBox2);
        // add(new NativeButton("get value",
        // e -> add(new Label(comboBox2.getValue() == null ? "null"
        // : comboBox2.getValue().getName()))));
        // add(new NativeButton("set value",
        // e -> comboBox2.setValue(people.get(3))));

    }

    private void createListDataProviderWithStrings() {
        ComboBox<String> comboBox = new ComboBox<>();
        comboBox.setId("lazy-strings");

        List<String> items = IntStream.range(0, 1000).mapToObj(i -> "Item " + i)
                .collect(Collectors.toList());
        ListDataProvider<String> dp = DataProvider.ofCollection(items);

        comboBox.setDataProvider(dp);

        NativeButton getButton = new NativeButton("get value", e -> {
            message.setText(comboBox.getValue());
        });
        getButton.setId("get-value");

        NativeButton setButton = new NativeButton("set value", e -> {
            comboBox.setValue(items.get(10));
        });
        setButton.setId("set-value");

        add(comboBox, getButton, setButton, message);
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
