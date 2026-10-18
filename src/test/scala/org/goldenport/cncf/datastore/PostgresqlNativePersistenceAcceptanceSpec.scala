package org.goldenport.cncf.datastore

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.EntityCreateProviderFixture.{DirectSession, race}
import org.goldenport.cncf.datastore.sql.{PostgreSqlDialectDriver, SqlDataStore}
import org.goldenport.cncf.directive.{Query as EntityQuery}
import org.goldenport.cncf.entity.{EntityCreateFailureSafetyFixture, EntityConcurrencyPolicy, EntityWritePolicy, RevisionPreconditionPolicy}
import org.goldenport.cncf.entity.{EntityPersistent, EntityStore, EntityStoreRowMapping, Phase100EntityStoreMappingFixture}
import org.goldenport.cncf.entity.EntityObservedDeleteCanonicalFixture.{reason, success}
import org.goldenport.record.{Field, Record}
import org.goldenport.observation.Descriptor
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}

/*
 * @since   Oct. 14, 2026
 * @version Oct. 18, 2026
 */
final class PostgresqlNativePersistenceAcceptanceSpec extends AnyWordSpec with Matchers with GivenWhenThen with PostgresqlAcceptance {
  import PostgresqlPersistenceFixture.*

  "Native PostgreSQL persistence" should {
    "retain declared mapping correspondence through native identifier conversion" which {
      Vector(false, true).foreach { normalized =>
        s"round trip a long Unicode storage name through mapped save and fresh-provider load with normalization $normalized" in {
          Given("a declared five-value mapping with a native-clipped payload name and a unique collection")
          with_postgresql { database =>
            val collection = EntityCollectionId("test", "phase100", database.token("mapped_clip"))
            val address = DataStore.CollectionId.EntityStore(collection)
            val entityid = org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "selected", collection, entropy = "selected")
            val field = "長" * 30
            val mapping = Phase100EntityStoreMappingFixture.mapping.copy(
              fields = Phase100EntityStoreMappingFixture.mapping.fields.map { declared =>
                if (declared.logicalName == "payload") declared.copy(storageName = field) else declared
              })
            given EntityPersistent[Record] = new EntityPersistent[Record] {
              def id(e: Record): EntityId = Phase100EntityStoreMappingFixture.persistent.id(e)
              def toRecord(e: Record): Record = Phase100EntityStoreMappingFixture.persistent.toRecord(e)
              def fromRecord(r: Record): Consequence[Record] = Phase100EntityStoreMappingFixture.persistent.fromRecord(r)
              override def storeRowMapping: Option[EntityStoreRowMapping] = Some(mapping)
            }
            val source = Phase100EntityStoreMappingFixture.row("nativeclip", "  { original formatting }\n雪  ")
              .upsertSingle("id", entityid.value)
            val nativefield = database.withConnection { connection =>
              val statement = connection.prepareStatement("SELECT CAST(? AS pg_catalog.name)::text")
              try {
                statement.setString(1, field)
                val rows = statement.executeQuery()
                try { rows.next(); rows.getString(1) } finally rows.close()
              } finally statement.close()
            }
            val writer = database.open(normalized)

            When("ordinary mapped save persists the row and the writer is closed")
            locally {
              given ExecutionContext = context(writer)
              try success(EntityStore.standard().save(source))
              finally success(writer.closeC())
            }

            When("a separately opened reader and context load the raw and logical rows")
            val reader = database.open(normalized)
            given ExecutionContext = context(reader)
            val physical = success(reader.load(address, DataStore.EntryId(entityid))).get
            val restored = success(EntityStore.standard().load[Record](entityid)).get
            val stored = database.snapshotAll()("public" -> address.collectionName)

            Then("the independent native name holds physical String JSON and every declared logical value is restored")
            nativefield should not be field
            physical.getAny(nativefield).get shouldBe a[String]
            physical.getString(nativefield) shouldBe Some(source.getAny("payload").get.asInstanceOf[io.circe.Json].noSpaces)
            physical.keySet.contains(field) shouldBe false
            stored.columns.map(_._1) should contain(nativefield)
            stored.columns.map(_._1) should not contain field
            Vector("original", "payload", "evidence", "labels", "summary", "number").foreach { name =>
              withClue(name) { restored.getAny(name) shouldBe source.getAny(name) }
            }
          }
        }

        s"reject complete mapped declarations with clipped native aliases before publication with normalization $normalized" in {
          Given("distinct payload and evidence storage names sharing the same clipped native prefix")
          with_postgresql { database =>
            val collection = EntityCollectionId("test", "phase100", database.token("mapped_clip_alias"))
            val entityid = org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "selected", collection, entropy = "selected")
            val common = "長" * 30
            val mapping = Phase100EntityStoreMappingFixture.mapping.copy(
              fields = Phase100EntityStoreMappingFixture.mapping.fields.map { declared =>
                if (declared.logicalName == "payload" || declared.logicalName == "evidence")
                  declared.copy(storageName = common + declared.logicalName)
                else declared
              })
            given EntityPersistent[Record] = new EntityPersistent[Record] {
              def id(e: Record): EntityId = Phase100EntityStoreMappingFixture.persistent.id(e)
              def toRecord(e: Record): Record = Phase100EntityStoreMappingFixture.persistent.toRecord(e)
              def fromRecord(r: Record): Consequence[Record] = Phase100EntityStoreMappingFixture.persistent.fromRecord(r)
              override def storeRowMapping: Option[EntityStoreRowMapping] = Some(mapping)
            }
            val writer = database.open(normalized)
            given ExecutionContext = context(writer)
            val source = Phase100EntityStoreMappingFixture.row("nativealias", "exact original")
              .upsertSingle("id", entityid.value)
            val before = database.snapshotAll()

            When("ordinary mapped save validates the complete provider-projected declaration")
            val result = EntityStore.standard().save(source)

            Then("the collision is a structured failure and every native schema and row remains unchanged")
            result shouldBe a[Consequence.Failure[?]]
            database.snapshotAll() shouldBe before
          }
        }
      }
    }

    "retain generic storage and query correspondence" which {
      Vector(false, true).foreach { normalize =>
        s"PG-E2 round trip physical scalars and JSON text, merge, query, projection and paging with normalization $normalize" in {
          Given("real independent native writer and reader pools and a legal mixed-case collection identity")
          with_postgresql { database =>
            val writer = database.open(normalize)
            val reader = database.open(normalize)
            given ExecutionContext = context(writer)
            val collection = DataStore.CollectionId(database.token("MixedCaseCrud"))
            val id = DataStore.StringEntryId("a'\\entry")
            val nested = "{\"kind\":\"document\",\"number\":7}"
            val sequence = "[\"日\",3,false]"
            val row = Record.dataAuto("displayName" -> "Before", "integral" -> 17L, "boolean" -> true,
              "floating" -> 1.25d, "structured" -> nested, "sequence" -> sequence)
            When("the actual native generic create, merge upsert and update execute")
            success(writer.create(collection, id, row))
            success(writer.save(collection, id, Record.dataAuto("displayName" -> "After")))
            success(writer.update(collection, id, Record.dataAuto("integral" -> 19L)))
            success(writer.save(collection, id, Record.empty))
            success(writer.create(collection, DataStore.StringEntryId("b"), row.upsertSingle("displayName", "Second")))
            success(writer.create(collection, DataStore.StringEntryId("c"), row.upsertSingle("displayName", "Third")))
            val loaded = success(reader.load(collection, id)).get
            val query = QueryDirective(Query.Expr(EntityQuery.Gte("integral", 17L)),
              projection = QueryProjection.Fields(Vector("displayName", "integral")),
              order = QueryOrder.By("displayName", OrderDirection.Asc), limit = QueryLimit.Limit(2), offset = 1)
            val selected = success(reader.search(collection, query))
            val unbounded = success(reader.search(collection, query.copy(limit = QueryLimit.Unbounded)))
            Then("the independently read scalars and encoded JSON-in-TEXT retain exact values and actual query semantics")
            loaded.getString("displayName") shouldBe Some("After")
            loaded.getLong("integral") shouldBe Some(19L)
            loaded.getLong("boolean") shouldBe Some(1L)
            loaded.getAny("floating") shouldBe Some(1.25d)
            loaded.getAny("structured") shouldBe Some(nested)
            loaded.getAny("sequence") shouldBe Some(sequence)
            selected.records.map(_.getString("displayName")) shouldBe Vector(Some("Second"), Some("Third"))
            selected.records.forall(_.keySet == Set("id", "displayName", "integral")) shouldBe true
            unbounded.records.map(_.asMap) shouldBe selected.records.map(_.asMap)
            success(reader.count(collection, query)) shouldBe 3
            And("generic native deletion removes only the selected id")
            success(writer.delete(collection, id))
            success(reader.load(collection, id)) shouldBe None
            success(reader.count(collection, QueryDirective(Query.Empty))) shouldBe 2
          }
        }
      }
      "PG-E2 execute CRUD with an exact quote, backslash, dot and Unicode physical column" in {
        Given("a lawful logical collection and a literal native column outside logical ID grammar")
        with_postgresql { database =>
          val writer = database.open()
          val reader = database.open()
          given ExecutionContext = context(writer)
          val collection = DataStore.CollectionId(database.token("MixedCasePhysicalFields"))
          val entry = DataStore.StringEntryId("selected")
          val field = "literal.\"back\\slash.雲"
          When("generic create, merge and update quote the actual field without altering its identity")
          success(writer.create(collection, entry, Record.dataAuto(field -> "Before", "retained" -> 17L)))
          success(writer.save(collection, entry, Record.dataAuto(field -> "Merged")))
          success(writer.update(collection, entry, Record.dataAuto(field -> "After")))
          val row = success(reader.load(collection, entry)).get
          val physical = database.snapshotAll()("public" -> collection.collectionName)
          Then("independent SQL and provider readers retain the exact column and business values")
          row.getString(field) shouldBe Some("After")
          row.getLong("retained") shouldBe Some(17L)
          physical.columns.map(_._1) should contain(field)
          physical.rows.find(_("id") == entry.print).get(field) shouldBe "After"
          When("generic delete addresses that lawful collection")
          success(writer.delete(collection, entry))
          Then("the independently observed native relation contains no selected row")
          success(reader.load(collection, entry)) shouldBe None
          database.snapshotAll()("public" -> collection.collectionName).rows shouldBe Vector.empty
        }
      }
      "PG-E2 preserve exact quoted case-distinct fields and put exact framework keys first" in {
        Given("a native create plan containing legitimate case-distinct id, revision and business columns")
        with_postgresql { database =>
          val writer = database.open()
          val setup = new DirectSession(database.token("pg_case_fields"), writer)
          val plan = setup.plan().copy(record = setup.plan().record ++ Record.dataAuto("Id" -> "Foreign", "Title" -> "Other", "Revision" -> 99L))
          When("the native provider creates and reads the actual SQL metadata keys")
          val created = success(setup.createC(plan))
          val durable = success(database.open().load(setup.rootCollection, setup.rootEntry)(using setup.context)).get
          Then("case-distinct columns stay distinct and exact id and managed revision remain authoritative")
          created.record.asMap shouldBe durable.asMap
          durable.fields.head.key shouldBe "id"
          durable.getString("Id") shouldBe Some("Foreign")
          durable.getString("title") shouldBe Some("Candidate")
          durable.getString("Title") shouldBe Some("Other")
          durable.getLong("cncf_revision") shouldBe Some(1L)
          durable.getLong("Revision") shouldBe Some(99L)
        }
      }
      "PG-E2 reject normalized field ambiguity before rows or first-use schema publication" in {
        Given("an actual normalized PostgreSQL writer and physical Foo/foo input aliases")
        with_postgresql { database =>
          val setup = new DirectSession(database.token("pg_normalized_alias"), database.open(true))
          val before = database.snapshotAll()
          When("strict create admits raw fields without last-wins collapse")
          val result = setup.createC(setup.plan().copy(record = Record.dataAuto("Foo" -> "First", "foo" -> "Second", "cncf_revision" -> 1L)))
          Then("ambiguity rejects and the complete native schema and row snapshot remains")
          result shouldBe a[Consequence.Failure[?]]
          database.snapshotAll() shouldBe before
        }
      }
      "PG-E2 retain noncolliding case-distinct read keys while omitting only SQL NULL attributes" in {
        Given("a lawful physical PostgreSQL table with quoted Foo/foo columns and independent readers")
        with_postgresql { database =>
          val collection = DataStore.CollectionId(database.token("pg_null_alias_read"))
          val entry = DataStore.StringEntryId("selected")
          database.statement(s"CREATE TABLE ${quote(collection.collectionName)} (id TEXT PRIMARY KEY, ${quote("Foo")} TEXT, ${quote("foo")} TEXT)")
          database.statement(s"INSERT INTO ${quote(collection.collectionName)} VALUES ('selected', NULL, 'retained'), ('unrelated', 'Other', 'other')")
          val normalized = database.open(true)
          val exact = database.open(false)
          given ExecutionContext = context(exact)
          val before = database.snapshotAll()
          When("both readers decode one NULL case-distinct column and the unrelated non-NULL row")
          val normalizedrow = success(normalized.load(collection, entry)).get
          val distinct = success(exact.load(collection, entry)).get
          val normalizedunrelated = success(normalized.load(collection, DataStore.StringEntryId("unrelated"))).get
          val unrelated = success(exact.load(collection, DataStore.StringEntryId("unrelated"))).get
          Then("both readers preserve separator-free case distinction and omit only the NULL attribute")
          normalizedrow.fields shouldBe Vector(Field("id", Field.Value.Single("selected")), Field("foo", Field.Value.Single("retained")))
          distinct.fields shouldBe Vector(Field("id", Field.Value.Single("selected")), Field("foo", Field.Value.Single("retained")))
          normalizedunrelated.fields shouldBe Vector(Field("id", Field.Value.Single("unrelated")),
            Field("Foo", Field.Value.Single("Other")), Field("foo", Field.Value.Single("other")))
          unrelated.fields shouldBe Vector(Field("id", Field.Value.Single("unrelated")),
            Field("Foo", Field.Value.Single("Other")), Field("foo", Field.Value.Single("other")))
          database.snapshotAll() shouldBe before
          Given("both case-distinct physical columns are SQL NULL and their identities remain present")
          database.statement(s"UPDATE ${quote(collection.collectionName)} SET ${quote("foo")}=NULL WHERE id='selected'")
          val bothnull = database.snapshotAll()
          When("both readers read the same existing row with two NULL business columns")
          val normalizedonlyid = success(normalized.load(collection, entry)).get
          val onlyid = success(exact.load(collection, entry)).get
          Then("both readers retain only the mandatory id and preserve the complete physical state")
          normalizedonlyid.fields shouldBe Vector(Field("id", Field.Value.Single("selected")))
          onlyid.fields shouldBe Vector(Field("id", Field.Value.Single("selected")))
          database.snapshotAll() shouldBe bothnull
        }
      }
      Vector(
        ("neither", "'left'", "'right'", Vector(Field("id", Field.Value.Single("selected")),
          Field("foo_bar", Field.Value.Single("left")), Field("fooBar", Field.Value.Single("right")))),
        ("left only", "NULL", "'right'", Vector(Field("id", Field.Value.Single("selected")),
          Field("fooBar", Field.Value.Single("right")))),
        ("right only", "'left'", "NULL", Vector(Field("id", Field.Value.Single("selected")),
          Field("foo_bar", Field.Value.Single("left")))),
        ("both", "NULL", "NULL", Vector(Field("id", Field.Value.Single("selected"))))
      ).foreach { case (mask, left, right, expected) =>
        s"PG-E2 reject actual decoded foo_bar/fooBar aliases before omission with $mask SQL NULL" in {
          Given("a lawful physical PostgreSQL table with quoted snake/camel aliases and a complete unrelated row")
          with_postgresql { database =>
            val collection = DataStore.CollectionId(database.token("pg_null_read_collision"))
            val entry = DataStore.StringEntryId("selected")
            database.statement(s"CREATE TABLE ${quote(collection.collectionName)} (id TEXT PRIMARY KEY, ${quote("foo_bar")} TEXT, ${quote("fooBar")} TEXT)")
            database.statement(s"INSERT INTO ${quote(collection.collectionName)} VALUES ('selected', $left, $right), ('unrelated', 'Other', 'other')")
            val normalized = database.open(true)
            val exact = database.open(false)
            given ExecutionContext = context(exact)
            val before = database.snapshotAll()
            val query = QueryDirective(Query.Empty, order = QueryOrder.By("id", OrderDirection.Asc))
            When("independent normalized and exact readers load both existing rows and search them in id order")
            val collision = normalized.load(collection, entry)
            val unrelatedcollision = normalized.load(collection, DataStore.StringEntryId("unrelated"))
            val searchcollision = normalized.search(collection, query)
            val distinct = success(exact.load(collection, entry)).get
            val unrelated = success(exact.load(collection, DataStore.StringEntryId("unrelated"))).get
            val exactsearch = success(exact.search(collection, query))
            Then("normalized load and search propagate configurationInvalid without partial success while exact readers preserve complete ordered raw Fields")
            Vector(
              "selected load: " -> collision,
              "unrelated load: " -> unrelatedcollision,
              "ordered search: " -> searchcollision
            ).foreach { case (label, result) =>
              withClue(label) {
                result shouldBe a[Consequence.Failure[?]]
                result match {
                  case Consequence.Failure(conclusion) => conclusion.status shouldBe Consequence.configurationInvalid("ambiguous decoded Record keys").conclusion.status
                  case _ => fail("Expected configurationInvalid for ambiguous decoded Record keys")
                }
              }
            }
            distinct.fields shouldBe expected
            unrelated.fields shouldBe Vector(Field("id", Field.Value.Single("unrelated")),
              Field("foo_bar", Field.Value.Single("Other")), Field("fooBar", Field.Value.Single("other")))
            exactsearch.records.map(_.fields) shouldBe Vector(expected,
              Vector(Field("id", Field.Value.Single("unrelated")),
                Field("foo_bar", Field.Value.Single("Other")), Field("fooBar", Field.Value.Single("other"))))
            database.snapshotAll() shouldBe before
          }
        }
      }
      "PG-E2 map native UTF-8 clipping to exact physical writes and authoritative decoded keys" in {
        Given("a native field longer than PostgreSQL's UTF-8 name carrier and a long literal table name")
        with_postgresql { database =>
          val writer = database.open()
          given ExecutionContext = context(writer)
          val collection = DataStore.CollectionId(database.token("LongMixedCase") + ("x" * 70))
          val entry = DataStore.StringEntryId("selected")
          val field = "長" * 30
          val (physicaltable, physical) = database.withConnection { connection =>
            val statement = connection.prepareStatement("SELECT CAST(? AS pg_catalog.name)::text")
            try {
              def _native_name_(name: String): String = {
                statement.setString(1, name)
                val rows = statement.executeQuery()
                try { rows.next(); rows.getString(1) } finally rows.close()
              }
              _native_name_(collection.collectionName) -> _native_name_(field)
            } finally statement.close()
          }
          success(writer.createEntity(EntityCreatePlan(collection, entry, Record.dataAuto(field -> "Before", "cncf_revision" -> 1L))))
          When("a native direct mutation addresses the original logical name and independent readback observes the physical key")
          val result = writer.mutateEntityDirect(EntityDirectMutationPlan(collection, entry, "cncf_revision",
            Record.dataAuto(field -> "After"), EntityMutationReadbackRequirement.AuthoritativeRecord))
          val row = success(database.open().load(collection, entry)).get
          Then("the exact backend-clipped column is updated once and the returned record uses actual SQL metadata")
          success(result).asInstanceOf[EntityMutationProviderResult.Applied].readback shouldBe EntityMutationProviderReadback.Authoritative(row)
          row.getString(physical) shouldBe Some("After")
          row.getLong("cncf_revision") shouldBe Some(2L)
          row.keySet.contains(field) shouldBe false
          database.snapshotAll()("public" -> physicaltable).rows.find(_("id") == entry.print).get(physical) shouldBe "After"
          database.snapshotAll().keySet.contains("public" -> collection.collectionName) shouldBe false
        }
      }
      "PG-E2 reject two raw input names with the same native clipped identity" in {
        Given("two different long Unicode names that PostgreSQL clips to the same physical column")
        with_postgresql { database =>
          val setup = new DirectSession(database.token("pg_clip_alias"), database.open())
          val before = database.snapshotAll()
          val common = "界" * 24
          When("native create admits actual name carriers before preparing rows")
          val result = setup.createC(setup.plan().copy(record = Record.dataAuto((common + "A") -> "First", (common + "B") -> "Second")))
          Then("both raw fields are visible to alias admission and full native state is retained")
          result shouldBe a[Consequence.Failure[?]]
          database.snapshotAll() shouldBe before
        }
      }
    }

    "resolve physical addresses and primary-id equality on the transaction connection" which {
      "PG-E8 follow the actual search_path through literal dot, quote and Unicode schema names" in {
        Given("two private native schemas with the same lawful mixed-case logical collection")
        with_postgresql { database =>
          val token = database.token("SearchPath")
          val firstschema = token + ".\"雲a"
          val secondschema = token + ".\"雲b"
          database.statement(s"CREATE SCHEMA ${quote(firstschema)}")
          database.statement(s"CREATE SCHEMA ${quote(secondschema)}")
          val collection = DataStore.CollectionId("LiteralMixedCase")
          val entry = DataStore.StringEntryId("selected")
          val first = database.open(searchPath = Some(quote(firstschema)))
          val second = database.open(searchPath = Some(quote(secondschema)))
          When("the two actual connections create independently through their selected schemas")
          success(first.createEntity(EntityCreatePlan(collection, entry, Record.dataAuto("title" -> "First", "cncf_revision" -> 1L)))(using context(first)))
          success(second.createEntity(EntityCreatePlan(collection, entry, Record.dataAuto("title" -> "Second", "cncf_revision" -> 1L)))(using context(second)))
          Then("each native address and independently reopened reader corresponds to its own selected relation")
          success(database.open(searchPath = Some(quote(firstschema))).load(collection, entry)(using context(first))).get.getString("title") shouldBe Some("First")
          success(database.open(searchPath = Some(quote(secondschema))).load(collection, entry)(using context(second))).get.getString("title") shouldBe Some("Second")
          val snapshots = database.snapshotAll()
          snapshots.keySet should contain allOf ((firstschema -> collection.collectionName), (secondschema -> collection.collectionName))
          snapshots(firstschema -> collection.collectionName).rows.find(_("id") == entry.print).get("title") shouldBe "First"
          snapshots(secondschema -> collection.collectionName).rows.find(_("id") == entry.print).get("title") shouldBe "Second"
          database.scalar(s"SELECT n.nspname FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace WHERE c.oid=pg_catalog.to_regclass('${quote(firstschema).replace("'", "''")}.${quote(collection.collectionName)}')") shouldBe firstschema
          database.scalar(s"SELECT n.nspname FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace WHERE c.oid=pg_catalog.to_regclass('${quote(secondschema).replace("'", "''")}.${quote(collection.collectionName)}')") shouldBe secondschema
        }
      }
      "PG-E8 preserve arbitrary quoted relation carriers at the JDBC and dialect layer" in {
        Given("an actual physical relation containing dot, quote, backslash and Unicode independently of CollectionId")
        with_postgresql { database =>
          val table = "Native 雲.\"back\\slash." + database.token("PhysicalRelation")
          val field = "value.\"雲\\field"
          val before = database.snapshotAll()
          When("the existing dialect creates, writes and reads its literal physical relation through JDBC")
          database.statement(PostgreSqlDialectDriver.createTableSql(table, Vector(field -> "value")))
          try {
            val observed = database.withConnection { connection =>
              val insert = connection.prepareStatement(PostgreSqlDialectDriver.insertSql(table, Vector(field)))
              try {
                insert.setString(1, "selected")
                insert.setString(2, "Actual quoted value")
                insert.executeUpdate()
              } finally insert.close()
              val select = connection.prepareStatement(PostgreSqlDialectDriver.selectByIdSql(table))
              try {
                select.setString(1, "selected")
                val rows = select.executeQuery()
                try { rows.next(); rows.getString(field) } finally rows.close()
              } finally select.close()
            }
            Then("independent native snapshots and exact-column readback correspond to the literal relation")
            observed shouldBe "Actual quoted value"
            database.snapshotAll()("public" -> table).rows shouldBe
              Vector(Map("id" -> "selected", field -> "Actual quoted value"))
            database.scalar(s"SELECT count(*) FROM (${PostgreSqlDialectDriver.tableExistsSql(table)}) selected") shouldBe "1"
          } finally database.statement(s"DROP TABLE ${quote(table)}")
          Then("the owned physical fixture leaves the previous complete native snapshot after removal")
          database.snapshotAll() shouldBe before
        }
      }
      "PG-E8 reject Instance/EntityStore aliases and native clipped relation aliases before effects" in {
        Given("a long physical root and differently typed/logically named collections reaching that same native relation")
        with_postgresql { database =>
          val setup = new DirectSession(database.token("pg_table_alias"), database.open())
          seedDirect(setup)
          val root = DataStore.CollectionId(database.token("pg_long_alias_") + ("x" * 70))
          success(setup.store.create(root, setup.oldEntry, Record.dataAuto("title" -> "Retained native root"))(using setup.context))
          val before = database.snapshotAll()
          val alias = DataStore.CollectionId.EntityStore(EntityCollectionId("other", "owner", root.collectionName + "different"))
          When("native address resolution admits root and side on the same actual connection")
          val result = setup.createC(setup.plan().copy(collection = root, sideEffects = Vector(EntityVersionedSideEffect.Delete(alias, setup.rootEntry))))
          Then("physical relation and native id equality reject the alias without schema or row publication")
          result shouldBe a[Consequence.Failure[?]]
          database.snapshotAll() shouldBe before
        }
      }
      "PG-E8 admit legitimate case-distinct relations without blanket case folding" in {
        Given("quoted native root and side names distinguished only by case")
        with_postgresql { database =>
          val setup = new DirectSession(database.token("pg_table_case"), database.open())
          seedDirect(setup)
          val alias = DataStore.CollectionId(setup.rootCollection.collectionName.toUpperCase(java.util.Locale.ROOT))
          val before = setup.snapshot(database.open())
          When("one native strict create writes distinct case-sensitive root and side targets")
          val result = setup.createC(setup.plan().copy(sideEffects = Vector(EntityVersionedSideEffect.Save(alias, setup.rootEntry, Record.dataAuto("title" -> "Separate")))))
          Then("both actual native tables retain their own identity and all older rows remain")
          success(result).record.getString("title") shouldBe Some("Candidate")
          success(database.open().load(alias, setup.rootEntry)(using setup.context)).get.getString("title") shouldBe Some("Separate")
          setup.snapshot(database.open()).oldroot shouldBe before.oldroot
          setup.snapshot(database.open()).oldside shouldBe before.oldside
        }
      }
      for { nondeterministic <- Vector(false, true); repeated <- Vector(false, true) } {
        s"PG-E8 use native id collation with nondeterministic $nondeterministic and repeated-side $repeated" in {
          Given("a real native text primary key using a deterministic or ICU nondeterministic catalog collation")
          with_postgresql { database =>
            val setup = new DirectSession(database.token(s"pg_collation_${nondeterministic}_$repeated"), database.open())
            val collation = if (nondeterministic) "Native 雲.\"ci_" + database.token("Collation") else "C"
            if (nondeterministic) database.statement(s"CREATE COLLATION ${quote(collation)} (provider=icu, locale='und-u-ks-level2', deterministic=false)")
            val target = if (repeated) setup.sideCollection else setup.rootCollection
            database.statement(s"CREATE TABLE ${quote(target.collectionName)} (${quote("id")} TEXT COLLATE ${quote(collation)} PRIMARY KEY)")
            seedDirect(setup)
            val before = database.snapshotAll()
            val effects = if (repeated) Vector(
              EntityVersionedSideEffect.Delete(target, DataStore.StringEntryId("FreshBody")),
              EntityVersionedSideEffect.Delete(target, DataStore.StringEntryId("FRESHBODY")))
            else Vector(EntityVersionedSideEffect.Save(target, DataStore.StringEntryId("FRESH"), Record.dataAuto("title" -> "Distinct native id")))
            When("root/side and repeated-side admission compare bound TEXT with actual catalog-qualified collation")
            val result = setup.createC(setup.plan().copy(sideEffects = effects))
            Then("native equality rejects aliases while deterministic distinct ids remain positively addressable")
            if (nondeterministic) {
              result shouldBe a[Consequence.Failure[?]]
              database.snapshotAll() shouldBe before
            } else {
              success(result).record.getString("title") shouldBe Some("Candidate")
              if (!repeated) success(database.open().load(target, DataStore.StringEntryId("FRESH"))(using setup.context)).get.getString("title") shouldBe Some("Distinct native id")
            }
          }
        }
      }
      Vector("composite", "deferrable", "bpchar", "domain", "citext", "nonprimary").foreach { schema =>
        s"PG-E8 reject the $schema native arbiter on every extant mutation target" in {
          Given("an actual extant side target with a schema outside the admitted immediate text primary-key boundary")
          with_postgresql { database =>
            val setup = new DirectSession(database.token(s"pg_unsupported_$schema"), database.open())
            val table = quote(setup.sideCollection.collectionName)
            val definition = schema match {
              case "composite" => "id TEXT, other TEXT, PRIMARY KEY(id,other)"
              case "deferrable" => "id TEXT PRIMARY KEY DEFERRABLE INITIALLY DEFERRED"
              case "bpchar" => "id CHAR(32) PRIMARY KEY"
              case "domain" =>
                val domain = database.token("pg_id_domain")
                database.statement(s"CREATE DOMAIN ${quote(domain)} AS TEXT")
                s"id ${quote(domain)} PRIMARY KEY"
              case "citext" => database.statement("CREATE EXTENSION IF NOT EXISTS citext"); "id CITEXT PRIMARY KEY"
              case _ => "id TEXT UNIQUE"
            }
            database.statement(s"CREATE TABLE $table ($definition)")
            val before = database.snapshotAll()
            When("the native create admits all extant addresses before any root or side row")
            val result = setup.createC(setup.plan().copy(sideEffects = Vector(EntityVersionedSideEffect.Delete(setup.sideCollection, setup.sideEntry))))
            Then("unsupported side schema fails structurally even without an alias pair and all native state stays unchanged")
            result shouldBe a[Consequence.Failure[?]]
            database.snapshotAll() shouldBe before
          }
        }
      }
      "PG-E8 leave an absent Delete-only side relation absent" in {
        Given("a real create plan whose only side effect deletes from an absent prospective relation")
        with_postgresql { database =>
          val setup = new DirectSession(database.token("pg_absent_delete"), database.open())
          val plan = setup.plan().copy(sideEffects = Vector(EntityVersionedSideEffect.Delete(setup.sideCollection, setup.sideEntry)))
          When("same-connection admission compares the prospective native target and executes strict create")
          val result = setup.createC(plan)
          Then("root creation succeeds without materializing unnecessary side schema")
          success(result).record.getString("title") shouldBe Some("Candidate")
          database.snapshotAll().keys.exists(_._2 == setup.sideCollection.collectionName) shouldBe false
        }
      }
      "PG-E8 reject an unsupported native relation before any root or side effect" in {
        Given("a real native view occupying the requested root identity and complete unrelated tables")
        with_postgresql { database =>
          val setup = new DirectSession(database.token("pg_view_relation"), database.open())
          database.statement(s"CREATE VIEW ${quote(setup.rootCollection.collectionName)} AS SELECT 'protected'::text AS id")
          val before = database.snapshotAll()
          When("same-connection native relation admission encounters the view")
          val result = setup.createC(setup.plan())
          Then("the unsupported physical relation rejects without side schema or row effects")
          result shouldBe a[Consequence.Failure[?]]
          database.snapshotAll() shouldBe before
          database.scalar(s"SELECT id FROM ${quote(setup.rootCollection.collectionName)}") shouldBe "protected"
        }
      }
      "PG-E8 reject an inaccessible selected schema without falling back to another native address" in {
        Given("a task-private schema and a restricted real database role without schema usage")
        with_postgresql { database =>
          val schema = database.token("PrivateSchema")
          val role = database.token("pg_limited_role")
          database.statement(s"CREATE SCHEMA ${quote(schema)}")
          database.statement(s"CREATE ROLE ${quote(role)}")
          database.statement(s"REVOKE ALL ON SCHEMA ${quote(schema)} FROM PUBLIC")
          val datasource = database.source(Some(quote(schema)))
          datasource.setOptions(s"-c role=$role")
          val writer = new SqlDataStore(PostgreSqlDialectDriver, datasource)
          try {
            val setup = new DirectSession(database.token("pg_inaccessible"), writer)
            val before = database.snapshotAll()
            When("a real restricted JDBC connection admits the requested schema and actual current_schema")
            val result = setup.createC(setup.plan())
            Then("access failure supplies structured rejection and leaves all native schemas and rows unchanged")
            result shouldBe a[Consequence.Failure[?]]
            database.snapshotAll() shouldBe before
            database.snapshotAll().keys.exists(_._2 == setup.rootCollection.collectionName) shouldBe false
          } finally success(writer.closeC())
        }
      }
      "PG-E4 keep non-primary unique collisions distinct from id duplicates and admitted side targets" in {
        Given("a deterministic side primary key and an additional native ICU unique id index with different equality")
        with_postgresql { database =>
          val setup = new DirectSession(database.token("pg_secondary_unique"), database.open())
          seedDirect(setup)
          val collation = database.token("pg_extra_ci")
          database.statement(s"CREATE COLLATION ${quote(collation)} (provider=icu, locale='und-u-ks-level2', deterministic=false)")
          database.statement(s"CREATE UNIQUE INDEX ${quote(database.token("pg_extra_idx"))} ON ${quote(setup.sideCollection.collectionName)} (${quote("id")} COLLATE ${quote(collation)})")
          success(setup.store.create(setup.sideCollection, DataStore.StringEntryId("FRESHBODY"), Record.dataAuto("content" -> "Protected outside primary-id target"))(using setup.context))
          val before = database.snapshotAll()
          When("a side Save collides only under the additional unique index and the actual primary constraint remains the sole update arbiter")
          val result = setup.createC(setup.plan())
          Then("the other constraint error fails and rolls back without a false root duplicate or collateral side update")
          reason(result) shouldBe Some("provider-failure")
          EntityCreateFailureSafetyFixture.taxonomy(result) should not be EntityCreateFailureSafetyFixture.taxonomy(Consequence.DataStoreDuplicate(setup.rootEntry.print))
          database.snapshotAll() shouldBe before
        }
      }
      "PG-E4 roll back a non-id unique root violation after actual side work without querying an aborted transaction" in {
        Given("an actual native root table with an additional business unique constraint and a full independent baseline")
        with_postgresql { database =>
          val setup = new DirectSession(database.token("pg_root_unique"), database.open())
          seedDirect(setup)
          success(setup.store.create(setup.rootCollection, DataStore.StringEntryId("other-business-owner"),
            Record.dataAuto("title" -> "Candidate", "cncf_revision" -> 7L))(using setup.context))
          database.statement(s"CREATE UNIQUE INDEX ${quote(database.token("pg_title_unique"))} ON ${quote(setup.rootCollection.collectionName)} (${quote("title")})")
          val before = database.snapshotAll()
          When("strict root insertion violates the business constraint after real side Save")
          val result = setup.createC(setup.plan())
          Then("the normalized SQL failure is not an id duplicate and complete root/body/unrelated state rolls back")
          reason(result) shouldBe Some("provider-failure")
          EntityCreateFailureSafetyFixture.taxonomy(result) should not be EntityCreateFailureSafetyFixture.taxonomy(Consequence.DataStoreDuplicate(setup.rootEntry.print))
          database.snapshotAll() shouldBe before
        }
      }
    }

    "retain native revision policies and authoritative mutation results" which {
      Vector(false, true).foreach { textrevision =>
        s"PG-E5 execute native direct and CAS updates with textual revision $textrevision" in {
          Given("a real native root with a valid integral or numeric-string revision and full unrelated data")
          with_postgresql { database =>
            val writer = database.open()
            val setup = new MutationSession(database.token(s"pg_native_$textrevision"), writer)
            setup.seed(if (textrevision) "1" else 1L)
            given ExecutionContext = setup.executionContext
            val reader = database.open()
            val before = database.snapshotAll()
            When("the direct native statement and then CAS update the exact managed revision once each")
            val direct = success(writer.mutateEntityDirect(EntityDirectMutationPlan(setup.rootCollection, setup.rootEntry,
              "cncf_revision", Record.dataAuto("title" -> "Direct"), EntityMutationReadbackRequirement.AuthoritativeRecord)))
            val cas = success(writer.compareAndSetEntity(EntityCompareAndSetMutationPlan(setup.rootCollection, setup.rootEntry,
              "cncf_revision", revision(2), Record.dataAuto("title" -> "CAS"), EntityMutationReadbackRequirement.AuthoritativeRecord)))
            val durable = setup.load(reader)
            Then("authoritative records reflect the actual native values and stale CAS cannot publish another change")
            direct shouldBe a[EntityMutationProviderResult.Applied]
            cas.asInstanceOf[EntityMutationProviderResult.Applied].readback shouldBe EntityMutationProviderReadback.Authoritative(durable)
            success(EntityRevision.createC(durable.getAny("cncf_revision").get)).value shouldBe 3L
            durable.getString("title") shouldBe Some("CAS")
            val committed = database.snapshotAll()
            success(writer.compareAndSetEntity(EntityCompareAndSetMutationPlan(setup.rootCollection, setup.rootEntry,
              "cncf_revision", revision(2), Record.dataAuto("title" -> "Stale")))) shouldBe EntityMutationProviderResult.Stale(revision(2), revision(3))
            database.snapshotAll() shouldBe committed
            committed("public" -> setup.sideCollection.collectionName) shouldBe before("public" -> setup.sideCollection.collectionName)
            committed("public" -> setup.successorCollection.collectionName) shouldBe before("public" -> setup.successorCollection.collectionName)
            When("strict observed native deletion matches the actual third revision and removes its admitted body")
            val deleted = success(writer.compareAndDeleteEntity(EntityCompareAndDeletePlan(setup.rootCollection, setup.rootEntry,
              "cncf_revision", revision(3), EntityDeleteRootMutation.Physical,
              sideEffects = Vector(EntityVersionedSideEffect.Delete(setup.sideCollection, setup.sideEntry)))))
            Then("numeric-string and integral revision carriers both produce the actual prior-match receipt and durable pair removal")
            deleted shouldBe EntityCompareAndDeleteResult.Deleted(revision(3))
            success(reader.load(setup.rootCollection, setup.rootEntry)) shouldBe None
            success(reader.load(setup.sideCollection, setup.sideEntry)) shouldBe None
          }
        }
      }
      Vector("missing", "null", "zero", "negative", "malformed", "exhausted", "text-exhausted").foreach { invalid =>
        s"PG-E5 preserve complete native state after $invalid revision admission" in {
          Given("a real native root with the selected invalid or exhausted revision carrier")
          with_postgresql { database =>
            val writer = database.open()
            val setup = new MutationSession(database.token(s"pg_revision_$invalid"), writer)
            val value: Any = invalid match {
              case "zero" => 0L
              case "negative" => -1L
              case "malformed" => "not-a-revision"
              case "exhausted" => Long.MaxValue
              case "text-exhausted" => Long.MaxValue.toString
              case _ => 1L
            }
            setup.seed(value)
            if (invalid == "missing") database.statement(s"ALTER TABLE ${quote(setup.rootCollection.collectionName)} DROP COLUMN ${quote("cncf_revision")}")
            if (invalid == "null") database.statement(s"UPDATE ${quote(setup.rootCollection.collectionName)} SET ${quote("cncf_revision")}=NULL WHERE ${quote("id")}='selected'")
            val before = database.snapshotAll()
            When("native direct mutation admits revision validity without unguarded casts or implicit initialization")
            val result = writer.mutateEntityDirect(EntityDirectMutationPlan(setup.rootCollection, setup.rootEntry,
              "cncf_revision", Record.dataAuto("title" -> "Rejected")))(using setup.executionContext)
            Then("a structured failure preserves every root, side, successor, schema and unrelated field")
            result shouldBe a[Consequence.Failure[?]]
            database.snapshotAll() shouldBe before
            When("the same invalid carrier reaches guarded mutation and observed native deletion")
            val expected = if (invalid == "exhausted" || invalid == "text-exhausted") Long.MaxValue else 1L
            val guarded = writer.mutateVersionedEntity(setup.updatePlan(expectedRevision = expected))(using setup.executionContext)
            val deletion = writer.compareAndDeleteEntity(EntityCompareAndDeletePlan(setup.rootCollection, setup.rootEntry,
              "cncf_revision", revision(expected), EntityDeleteRootMutation.Physical,
              sideEffects = Vector(EntityVersionedSideEffect.Delete(setup.sideCollection, setup.sideEntry))))(using setup.executionContext)
            Then("both native boundaries reject without resetting revision or publishing root/body changes")
            guarded shouldBe a[Consequence.Failure[?]]
            deletion shouldBe a[Consequence.Failure[?]]
            database.snapshotAll() shouldBe before
          }
        }
      }
      "PG-E5 preserve stale-before-equality, ordinary no-op ordering and atomic changed body readback" in {
        Given("a real native revisioned root/body pair and separate observer pool")
        with_postgresql { database =>
          val writer = database.open()
          val setup = new MutationSession(database.token("pg_guarded"), writer)
          setup.seed()
          given ExecutionContext = setup.executionContext
          val reader = database.open()
          val before = database.snapshotAll()
          When("observed equality is stale, ordinary optimistic equality is a no-op, and a current change executes")
          success(writer.mutateVersionedEntity(setup.updatePlan("Before", 2L))) shouldBe EntityVersionedMutationResult.Stale(revision(2), revision(1))
          val noop = success(writer.mutateVersionedEntity(setup.updatePlan("Before", 2L).copy(preconditionPolicy = RevisionPreconditionPolicy.Managed)))
          noop shouldBe EntityVersionedMutationResult.NoOp(setup.load(reader))
          database.snapshotAll() shouldBe before
          val applied = success(writer.mutateVersionedEntity(setup.updatePlan()))
          Then("only the admitted change publishes the exact root/body pair and authoritative next revision")
          applied shouldBe EntityVersionedMutationResult.Applied(setup.load(reader))
          setup.load(reader).getLong("cncf_revision") shouldBe Some(2L)
          success(reader.load(setup.sideCollection, setup.sideEntry)).get.getString("content") shouldBe Some("Changed complete body")
          database.snapshotAll()("public" -> setup.successorCollection.collectionName) shouldBe before("public" -> setup.successorCollection.collectionName)
        }
      }
      "PG-E5 map normalized changes and exclusions to actual decoded keys without losing null markers" in {
        Given("a normalized native writer with a clipped physical change field and an excluded audit field")
        with_postgresql { database =>
          val writer = database.open(true)
          given ExecutionContext = context(writer)
          val collection = DataStore.CollectionId(database.token("pg_mapped_fields"))
          val entry = DataStore.StringEntryId("selected")
          val field = "long_business_field_" + ("x" * 70)
          success(writer.createEntity(EntityCreatePlan(collection, entry, Record.dataAuto(field -> "Before", "audit_note" -> "Old", "cncf_revision" -> 1L))))
          val before = database.snapshotAll()
          val plan = versionedPlan(collection, entry, Record.dataAuto(field -> "Before", "audit_note" -> "New"))
            .copy(comparisonExcludedFields = Set("audit_note"))
          When("business comparison resolves physical names and decoded Record keys on the actual transaction connection")
          val result = writer.mutateVersionedEntity(plan)
          Then("only the excluded value differs and no-op leaves complete native state unchanged")
          success(result) shouldBe a[EntityVersionedMutationResult.NoOp]
          database.snapshotAll() shouldBe before
          When("a native direct clear marker assigns physical NULL through the admitted field mapping")
          val direct = success(writer.mutateEntityDirect(EntityDirectMutationPlan(collection, entry, "cncf_revision",
            Record.dataAuto("audit_note" -> org.simplemodeling.model.directive.Update.SetNull), EntityMutationReadbackRequirement.AuthoritativeRecord)))
          val reader = database.open(true)
          val cleared = success(reader.load(collection, entry)).get
          val clearphysical = database.snapshotAll()("public" -> collection.collectionName)
          Then("the actual direct readback omits auditNote while the exact column remains physically NULL at revision two")
          direct shouldBe EntityMutationProviderResult.Applied(EntityMutationProviderReadback.Authoritative(cleared))
          cleared.keySet.contains("auditNote") shouldBe false
          clearphysical.columns.map(_._1) should contain("audit_note")
          java.util.Objects.isNull(clearphysical.rows.find(_("id") == entry.print).get("audit_note")) shouldBe true
          cleared.getLong("cncf_revision") shouldBe Some(2L)
          Given("the independent reader observes the native SQL NULL audit field at current revision two")
          val physical = before("public" -> collection.collectionName).columns.find(_._1.startsWith("long_business_field_")).get._1
          val businesskey = cleared.fields.find(_.value.single == "Before").get.key
          When("a guarded versioned patch changes the long business field while retaining the cleared audit field")
          val changed = success(writer.mutateVersionedEntity(versionedPlan(collection, entry,
            Record.dataAuto(field -> "After"), expectedRevision = 2L)))
          val updated = success(reader.load(collection, entry)).get
          val durable = database.snapshotAll()("public" -> collection.collectionName).rows.find(_("id") == entry.print).get
          Then("the physical and decoded business values change once while readback omits the physically retained SQL NULL")
          changed shouldBe EntityVersionedMutationResult.Applied(updated)
          durable(physical) shouldBe "After"
          durable.contains("audit_note") shouldBe true
          java.util.Objects.isNull(durable("audit_note")) shouldBe true
          updated.getString(businesskey) shouldBe Some("After")
          updated.getLong("cncf_revision") shouldBe Some(3L)
          updated.keySet.contains("auditNote") shouldBe false
        }
      }
      "PG-E5 enforce normalized exclusion guards through native diagnosis" in {
        Given("a actual native closed root and a guard excluding that stored state")
        with_postgresql { database =>
          val writer = database.open(true)
          val setup = new MutationSession(database.token("pg_exclusion"), writer)
          setup.seed(status = "closed")
          val before = database.snapshotAll()
          When("the native direct statement observes the guard and diagnoses its zero-row outcome")
          val result = writer.mutateEntityDirect(EntityDirectMutationPlan(setup.rootCollection, setup.rootEntry,
            "cncf_revision", Record.dataAuto("title" -> "Forbidden"), exclusionGuards = Vector(EntityMutationExclusionGuard.EqualTo("status", "closed"))))(using setup.executionContext)
          Then("the selected state remains unavailable with no storage mutation")
          result shouldBe a[Consequence.Failure[?]]
          database.snapshotAll() shouldBe before
        }
      }
      for {
        carrier <- Vector("TEXT", "VARCHAR(4)", "BIGINT", "DOUBLE PRECISION")
        operation <- Vector("direct", "cas", "physical", "logical")
        blocked <- Vector(false, true)
      } {
        s"PG-E5/PG-E6 bind the actual $carrier guard for $operation with excluded state $blocked" in {
          Given("a real native scalar guard column, numeric exclusion value and complete independently observed state")
          with_postgresql { database =>
            val writer = database.open()
            val reader = database.open()
            val setup = new MutationSession(database.token("TypedGuard"), writer)
            setup.seed()
            given ExecutionContext = setup.executionContext
            val field = "GuardState"
            database.statement(s"ALTER TABLE ${quote(setup.rootCollection.collectionName)} ADD COLUMN ${quote(field)} $carrier")
            val numeric = if (blocked) 0 else 1
            val literal = if (carrier == "TEXT" || carrier.startsWith("VARCHAR")) s"'$numeric'" else numeric.toString
            database.statement(s"UPDATE ${quote(setup.rootCollection.collectionName)} SET ${quote(field)}=$literal WHERE ${quote("id")}='selected'")
            val guardvalue: Any = if (carrier == "DOUBLE PRECISION") {
              if (operation == "physical" || operation == "logical") BigDecimal("0.0") else 0.0d
            } else 0L
            val guards = Vector(EntityMutationExclusionGuard.EqualTo(field, guardvalue))
            val before = database.snapshotAll()
            val oldrow = setup.load(reader)
            When("one native direct, CAS or observed physical/logical deletion uses the existing encoded scalar")
            val result: Consequence[Any] = operation match {
              case "direct" => writer.mutateEntityDirect(EntityDirectMutationPlan(setup.rootCollection, setup.rootEntry,
                "cncf_revision", Record.dataAuto("title" -> "Typed change"), EntityMutationReadbackRequirement.AuthoritativeRecord,
                exclusionGuards = guards)).map(value => value: Any)
              case "cas" => writer.compareAndSetEntity(EntityCompareAndSetMutationPlan(setup.rootCollection, setup.rootEntry,
                "cncf_revision", revision(1), Record.dataAuto("title" -> "Typed change"), EntityMutationReadbackRequirement.AuthoritativeRecord,
                exclusionGuards = guards)).map(value => value: Any)
              case _ => writer.compareAndDeleteEntity(EntityCompareAndDeletePlan(setup.rootCollection, setup.rootEntry,
                "cncf_revision", revision(1),
                if (operation == "physical") EntityDeleteRootMutation.Physical
                else EntityDeleteRootMutation.Logical(Record.dataAuto("title" -> "Typed change")),
                exclusionGuards = guards)).map(value => value: Any)
            }
            Then("exclusion retains complete native state or one admitted mutation has exact authoritative revision and readback")
            if (blocked) {
              result shouldBe a[Consequence.Failure[?]]
              EntityCreateFailureSafetyFixture.taxonomy(result) shouldBe
                EntityCreateFailureSafetyFixture.taxonomy(Consequence.entityNotFound("excluded native state"))
              database.snapshotAll() shouldBe before
              setup.load(reader).fields shouldBe oldrow.fields
            } else {
              val key = "public" -> setup.rootCollection.collectionName
              val expectedrows = before(key).rows.flatMap { row =>
                if (row("id") != setup.rootEntry.print) Vector(row)
                else if (operation == "physical") Vector.empty
                else Vector(row ++ Map("title" -> "Typed change", "cncf_revision" -> 2L))
              }
              database.snapshotAll() shouldBe before.updated(key, before(key).copy(rows = expectedrows))
              if (operation == "physical") {
                success(result) shouldBe EntityCompareAndDeleteResult.Deleted(revision(1))
                success(reader.load(setup.rootCollection, setup.rootEntry)) shouldBe None
              } else {
                val durable = setup.load(reader)
                durable.getLong("cncf_revision") shouldBe Some(2L)
                durable.getString("title") shouldBe Some("Typed change")
                durable.fields.filterNot(field => Set("title", "cncf_revision").contains(field.key)) shouldBe
                  oldrow.fields.filterNot(field => Set("title", "cncf_revision").contains(field.key))
                if (operation == "logical") success(result) shouldBe EntityCompareAndDeleteResult.Deleted(revision(1))
                else success(result).asInstanceOf[EntityMutationProviderResult.Applied].readback shouldBe
                  EntityMutationProviderReadback.Authoritative(durable)
              }
            }
          }
        }
      }
      for {
        floating <- Vector("Float", "Double")
        logical <- Vector(false, true)
      } {
        s"PG-E6 reject raw $floating guards for logical deletion $logical without native effects" in {
          Given("a lawful native root, complete body/revision state and a real DOUBLE PRECISION guard column")
          with_postgresql { database =>
            val writer = database.open()
            val reader = database.open()
            val setup = new MutationSession(database.token("RawFloatingGuard"), writer)
            setup.seed()
            given ExecutionContext = setup.executionContext
            val field = "GuardState"
            database.statement(s"ALTER TABLE ${quote(setup.rootCollection.collectionName)} ADD COLUMN ${quote(field)} DOUBLE PRECISION")
            database.statement(s"UPDATE ${quote(setup.rootCollection.collectionName)} SET ${quote(field)}=1 WHERE ${quote("id")}='selected'")
            val value: Any = if (floating == "Float") (0.0f: Any) else (0.0d: Any)
            val expected = DataStoreConditionalValue.from(value)
            val before = database.snapshotAll()
            val oldrow = setup.load(reader)
            When("the actual compare-and-delete API admits a raw floating EqualTo exclusion guard")
            val result = writer.compareAndDeleteEntity(EntityCompareAndDeletePlan(setup.rootCollection, setup.rootEntry,
              "cncf_revision", revision(1),
              if (logical) EntityDeleteRootMutation.Logical(Record.dataAuto("title" -> "Rejected change"))
              else EntityDeleteRootMutation.Physical,
              exclusionGuards = Vector(EntityMutationExclusionGuard.EqualTo(field, value))))
            Then("the existing exact-value taxonomy and policy reject before altering schema, root, body or revision")
            expected shouldBe a[Consequence.Failure[?]]
            result shouldBe a[Consequence.Failure[?]]
            EntityCreateFailureSafetyFixture.taxonomy(result) shouldBe EntityCreateFailureSafetyFixture.taxonomy(expected)
            val policies = result match {
              case Consequence.Failure(conclusion) => conclusion.observation.cause.descriptor.facets.collect {
                case Descriptor.Facet.Policy(policy) => policy
              }.toSet
              case _ => Set.empty[String]
            }
            policies should contain("entity-conditional-transition.exact-value")
            database.snapshotAll() shouldBe before
            setup.load(reader).fields shouldBe oldrow.fields
            setup.load(reader).getLong("cncf_revision") shouldBe Some(1L)
          }
        }
      }
      "PG-E5 arbitrate independent CAS writers on real native connections" in {
        Given("two actual PostgreSQL writers with the same observed revision")
        with_postgresql { database =>
          val first = database.open()
          val second = database.open()
          val setup = new MutationSession(database.token("pg_cas_race"), first)
          setup.seed()
          val before = database.snapshotAll()
          def _attempt_(store: SqlDataStore, title: String): Consequence[EntityMutationProviderResult] =
            store.compareAndSetEntity(EntityCompareAndSetMutationPlan(setup.rootCollection, setup.rootEntry,
              "cncf_revision", EntityRevision.INITIAL, Record.dataAuto("title" -> title), EntityMutationReadbackRequirement.AuthoritativeRecord))(using setup.executionContext)
          When("both independent native statements race without application exclusion")
          val results = race(() => _attempt_(first, "First"), () => _attempt_(second, "Second"))
          val winners = results.flatMap(_.toOption).collect { case applied: EntityMutationProviderResult.Applied => applied }
          val losers = results.flatMap(_.toOption).collect { case stale: EntityMutationProviderResult.Stale => stale }
          val durable = setup.load(database.open())
          Then("one whole authoritative winner advances once and the loser observes its real revision")
          winners.size shouldBe 1
          losers shouldBe Vector(EntityMutationProviderResult.Stale(revision(1), revision(2)))
          winners.head.readback shouldBe EntityMutationProviderReadback.Authoritative(durable)
          database.snapshotAll()("public" -> setup.sideCollection.collectionName) shouldBe before("public" -> setup.sideCollection.collectionName)
        }
      }
    }
  }
}
