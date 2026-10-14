package dev.monkeypatch.rctiming.persistence;

import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.SortField;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UpdatableRecord;
import org.jooq.impl.DSL;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Base for the jOOQ repositories that replace Spring Data JPA (#69). It gives each repository the
 * methods callers used from {@code JpaRepository}, so moving an entity to jOOQ leaves its callers
 * alone. A subclass maps its entity to and from the table's record, field by field.
 *
 * <p>Unlike Hibernate there is no dirty checking: a changed entity reaches the database only when
 * it is passed to {@link #save}. Writes run in a transaction, joining the caller's if there is
 * one, so they use the write connection. Reads outside a transaction use the read pool.
 *
 * <p>There are no row locks. SQLite's write pool has one connection, so write transactions run one
 * at a time: a read-then-write inside one cannot interleave with another. The {@code findByIdForUpdate}
 * methods are plain {@link #findById} named for that use, and must be called inside the transaction
 * that makes the change. This rests on the vendor allowing one write connection; a vendor with more
 * would need real row locks here.
 *
 * <p>{@link #save} stamps the creation time of a {@link CreatedAt} entity that has none, and the update time of
 * an {@link UpdatedAt} entity every time, so callers don't.
 *
 * <p>{@link #getOrThrow} and {@link #requireExists} raise the domain's {@link EntityNotFoundException},
 * which the API answers with 404, so callers need not repeat the lookup.
 *
 * @param <E> the entity
 * @param <R> the table's generated record
 */
public abstract class JooqRepository<E, R extends UpdatableRecord<R>> {

    protected final DSLContext dsl;
    protected final Table<R> table;
    protected final TableField<R, Long> id;

    protected JooqRepository(DSLContext dsl, Table<R> table, TableField<R, Long> id) {
        this.dsl = dsl;
        this.table = table;
        this.id = id;
    }

    /** Builds an entity from a row. */
    protected abstract E toEntity(R record);

    /** Copies every column except the id from the entity into the record. */
    protected abstract void toRecord(E entity, R record);

    protected abstract Long idOf(E entity);

    protected abstract void setId(E entity, Long id);

    /** Columns written on insert and never changed by an update, such as a creation time. */
    protected List<Field<?>> insertOnly() {
        return List.of();
    }

    public Optional<E> findById(Long entityId) {
        return findOne(id.eq(entityId));
    }

    /** The entity, or an {@link EntityNotFoundException} naming it, which the API answers with 404. */
    public E getOrThrow(Long entityId) {
        return findById(entityId).orElseThrow(() -> notFound(entityId));
    }

    /** Throws an {@link EntityNotFoundException}, which the API answers with 404, when there is no such row. */
    public void requireExists(Long entityId) {
        if (!existsById(entityId)) {
            throw notFound(entityId);
        }
    }

    /**
     * What one row is called in a not-found message, such as "Racing class". By default it comes from the
     * table's name, made singular.
     */
    protected String entityName() {
        String name = table.getName().toLowerCase().replace('_', ' ');
        if (name.endsWith("ies")) {
            name = name.substring(0, name.length() - 3) + "y";
        } else if (name.endsWith("sses")) {
            name = name.substring(0, name.length() - 2);
        } else if (name.endsWith("s")) {
            name = name.substring(0, name.length() - 1);
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private EntityNotFoundException notFound(Long entityId) {
        return new EntityNotFoundException(entityName() + " not found: " + entityId);
    }

    public List<E> findAll() {
        return findWhere(null);
    }

    public List<E> findAllById(Iterable<Long> ids) {
        List<Long> wanted = new ArrayList<>();
        ids.forEach(wanted::add);
        return wanted.isEmpty() ? List.of() : findWhere(id.in(wanted));
    }

    public boolean existsById(Long entityId) {
        return dsl.fetchExists(table, id.eq(entityId));
    }

    public long count() {
        return dsl.fetchCount(table);
    }

    /** Inserts the entity when it has no id, setting the new id on it, and updates it otherwise. */
    @Transactional
    public E save(E entity) {
        stamp(entity);
        R record = dsl.newRecord(table);
        toRecord(entity, record);
        Long entityId = idOf(entity);
        if (entityId == null) {
            record.changed(id, false);
            record.insert();
            setId(entity, record.get(id));
            return entity;
        }
        record.set(id, entityId);
        record.changed(id, false);
        insertOnly().forEach(field -> record.changed(field, false));
        int updated = dsl.update(table).set(record).where(id.eq(entityId)).execute();
        if (updated == 0) {
            // As JPA's merge does, an entity whose row has gone is inserted again with its id
            record.changed(id, true);
            insertOnly().forEach(field -> record.changed(field, true));
            dsl.insertInto(table).set(record).execute();
        }
        return entity;
    }

    /** Stamped to the microsecond, as the columns store it, so a saved entity equals the row read back. */
    private static void stamp(Object entity) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        if (entity instanceof CreatedAt created && created.getCreatedAt() == null) {
            created.setCreatedAt(now);
        }
        if (entity instanceof UpdatedAt updated) {
            updated.setUpdatedAt(now);
        }
    }

    @Transactional
    public List<E> saveAll(Iterable<E> entities) {
        List<E> saved = new ArrayList<>();
        entities.forEach(entity -> saved.add(save(entity)));
        return saved;
    }

    @Transactional
    public void deleteById(Long entityId) {
        dsl.deleteFrom(table).where(id.eq(entityId)).execute();
    }

    @Transactional
    public void delete(E entity) {
        deleteById(idOf(entity));
    }

    @Transactional
    public void deleteAll(Iterable<? extends E> entities) {
        entities.forEach(this::delete);
    }

    @Transactional
    public void deleteAll() {
        dsl.deleteFrom(table).execute();
    }

    /** The one entity matching the condition, if any. */
    protected Optional<E> findOne(Condition condition) {
        return dsl.selectFrom(table).where(condition).fetchOptional().map(this::toEntity);
    }

    /** Every entity matching the condition (all of them when it is null), in id order. */
    protected List<E> findWhere(Condition condition) {
        return findWhere(condition, id.asc());
    }

    protected List<E> findWhere(Condition condition, SortField<?>... order) {
        return dsl.selectFrom(table)
                .where(condition == null ? DSL.noCondition() : condition)
                .orderBy(order)
                .fetch(this::toEntity);
    }
}
