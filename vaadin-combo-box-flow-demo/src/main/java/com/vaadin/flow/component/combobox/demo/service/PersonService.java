package com.vaadin.flow.component.combobox.demo.service;

import com.vaadin.flow.component.combobox.demo.data.PersonData;
import com.vaadin.flow.component.combobox.demo.entity.Person;
import com.vaadin.flow.data.provider.Query;

import java.util.List;
import java.util.stream.Stream;

public class PersonService {
    private final PersonData personData;

    public PersonService() {
        this.personData = new PersonData();
    }

    public PersonService(int personCount) {
        this.personData = new PersonData(personCount);
    }

    public Stream<Person> fetch(Query<Person, String> query) {
        return personData.getPersons().stream()
                .filter(person -> query.getFilter()
                        .map(filter -> person.toString().toLowerCase()
                                .startsWith(filter.toLowerCase()))
                        .orElse(true))
                .skip(query.getOffset()).limit(query.getLimit());
    }

    public int count(Query<Person, String> query) {
        return (int) personData.getPersons().stream()
                .filter(person -> query.getFilter()
                        .map(filter -> person.toString().toLowerCase()
                                .startsWith(filter.toLowerCase()))
                        .orElse(true))
                .count();
    }

    public Stream<Person> fetchPage(String filter, int page, int pageSize) {
        return fetch(
                new Query<>(page * pageSize, pageSize, null, null, filter));
    }

    public int count() {
        return personData.getPersons().size();
    }

    public List<Person> fetchAll() {
        return personData.getPersons();
    }
}
