/*
  ~ Copyright 2000-2018 Vaadin Ltd.
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License"); you may not
  ~ use this file except in compliance with the License. You may obtain a copy of
  ~ the License at
  ~
  ~ http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
  ~ WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
  ~ License for the specific language governing permissions and limitations under
  ~ the License.
  */
/*
  FIXME(polymer-modulizer): the above comments were extracted
  from HTML and may be out of place here. Review them and
  then delete this comment!
*/
class WrapperTemplate extends Polymer.Element {
  static get template() {
    return Polymer.html`
        <combo-box-in-a-template id="comboBoxInATemplate"></combo-box-in-a-template>
        <combo-box-in-a-template2 id="comboBoxInATemplate2"></combo-box-in-a-template2>
`;
  }

  static get is() {
      return 'wrapper-template'
  }
}
customElements.define(WrapperTemplate.is, WrapperTemplate);
