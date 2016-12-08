/**
 * Copyright 2015 Confluent Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package com.hortonworks.kafkarest.entities;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.hortonworks.registries.schemaregistry.SchemaIdVersion;
import org.hibernate.validator.constraints.NotEmpty;

import java.util.List;

public class ProduceResponse {

  @NotEmpty
  private List<PartitionOffset> offsets;

  private SchemaIdVersion keySchemaId;

  private SchemaIdVersion valueSchemaId;

  @JsonProperty
  public List<PartitionOffset> getOffsets() {
    return offsets;
  }

  @JsonProperty
  public void setOffsets(List<PartitionOffset> offsets) {
    this.offsets = offsets;
  }

  @JsonProperty("key_schema_id")
  public SchemaIdVersion getKeySchemaId() {
    return keySchemaId;
  }

  public void setKeySchemaId(SchemaIdVersion keySchemaId) {
    this.keySchemaId = keySchemaId;
  }

  @JsonProperty("value_schema_id")
  public SchemaIdVersion getValueSchemaId() {
    return valueSchemaId;
  }

  public void setValueSchemaId(SchemaIdVersion valueSchemaId) {
    this.valueSchemaId = valueSchemaId;
  }
}
