package ru.circumflex.orm

import collection.mutable.ListBuffer
import ORM._
import java.lang.reflect.Constructor
import java.sql.PreparedStatement
import ru.circumflex.core.Circumflex
import ru.circumflex.orm.Column.BinaryColumn
import ru.circumflex.orm.Column.BooleanColumn
import ru.circumflex.orm.Column.DateColumn
import ru.circumflex.orm.Column.IntegerColumn
import ru.circumflex.orm.Column.LongColumn
import ru.circumflex.orm.Column.NumericColumn
import ru.circumflex.orm.Column.StringColumn
import ru.circumflex.orm.Column.TimeColumn
import ru.circumflex.orm.Column.TimestampColumn
import ru.circumflex.orm.Constraint.AssociativeForeignKey
import ru.circumflex.orm.Constraint.CheckConstraint
import ru.circumflex.orm.Constraint.ForeignKey
import ru.circumflex.orm.Constraint.MultiForeignKey
import ru.circumflex.orm.Constraint.PhysicalPrimaryKey
import ru.circumflex.orm.Constraint.PrimaryKey
import ru.circumflex.orm.Constraint.UniqueKey
import ru.circumflex.orm.Constraint.VirtualPrimaryKey
import ru.circumflex.orm.Predicate.SimpleExpression
import ru.circumflex.orm.Query.QueryHelper
import ru.circumflex.orm.Validator.RecordFieldValidator
import ru.circumflex.orm.Validator.RecordValidator

/**
 * Designates a relation that can be used to retrieve certain type of records.
 * It can be considered a table, a virtual table, a view, a subquery, etc.
 */
abstract class Relation[R](implicit m: Manifest[R]) extends JDBCHelper with QueryHelper {

  protected var _primaryKey: PrimaryKey[_, R] = new VirtualPrimaryKey(this, relationName + "_vkey")

  protected val _validators = new ListBuffer[RecordValidator[R]]
  protected val _columns = new ListBuffer[Column[_, R]]
  protected val _constraints = new ListBuffer[Constraint[R]]
  protected val _associations = new ListBuffer[Association[R, _]]
  protected val _preAuxiliaryObjects = new ListBuffer[SchemaObject]
  protected val _postAuxiliaryObjects = new ListBuffer[SchemaObject]

  private var _cachedRecordClass: Class[R] = null
  private var _cachedRecordConstructor: Constructor[R] = null
  private var _cachedRelationName: String = null

  protected var _prefetchSeq: Seq[Association[Any, Any]] = Nil

  private var uniqueCounter = -1

  protected[orm] def uniqueSuffix: String = {
    uniqueCounter += 1
    if (uniqueCounter == 0) "" else "_" + uniqueCounter
  }

  /**
   * Determines, whether DML operations are allowed on this relation.
   */
  def readOnly: Boolean = false

  /**
   * Returns a class of record which this relation describes.
   * @by Caoyuan
   */
  def recordClass: Class[R] = {
    if (_cachedRecordClass == null)
      _cachedRecordClass = Class.forName(
        m.erasure.getName,
        //this.getClass.getName.replaceAll("(.*)\\$$", "$1"),
        true, Circumflex.classLoader
      ).asInstanceOf[Class[R]]

    _cachedRecordClass
  }

  /**
   * Returns a instance of record which this relation describes.
   * @by Caoyuan
   */
  def newRecordInstance: R = {
    if (_cachedRecordConstructor == null) {
      val constructors = recordClass.getDeclaredConstructors
      var i = 0
      while (i < constructors.length && _cachedRecordConstructor == null) {
        val c = constructors(i)
        val len = c.getParameterTypes.length
        if (len == 0 || len == 1) {
          _cachedRecordConstructor = c.asInstanceOf[Constructor[R]]
        }
        i += 1
      }
    }
    
    val initArgsLength = _cachedRecordConstructor.getParameterTypes.length
    if (initArgsLength == 0)
      _cachedRecordConstructor.newInstance()
    else
      _cachedRecordConstructor.newInstance(this)
  }

  /**
   * The mandatory primary key constraint for this relation.
   */
  def primaryKey: PrimaryKey[_, R] = _primaryKey

  /**
   * Returns Schema object, that will contain specified table.
   * Defaults to DefaultSchema singleton.
   */
  def schema: Schema = Schema.Default

  /**
   * Provides schema name.
   */
  def schemaName: String = schema.schemaName

  /**
   * Unqualified relation name. Defaults to unqualified record class name
   * (camel-case-to-underscored).
   */
  def relationName: String = {
    if (_cachedRelationName == null) {
      val relationName = getClass.getSimpleName
      _cachedRelationName = relationName.substring(0, relationName.length - 1) // strip end '$'
      .replaceAll("([A-Z])", "_$1")
      .replaceAll("^_(.*)", "$1")
      .toLowerCase
    }
    
    _cachedRelationName
  }

  /**
   * Returns relation's qualified name.
   */
  def qualifiedName: String = dialect.qualifyRelation(this)

  /**
   * Returns validators that correspond to this relation.
   */
  def validators: Seq[RecordValidator[R]] = _validators

  /**
   * Returns columns that correspond to this relation.
   */
  def columns: Seq[Column[_, R]] = _columns

  /**
   * Returns constraints that correspond to this relation.
   */
  def constraints: Seq[Constraint[R]] = _constraints

  /**
   * Returns associations that correspond to this relation.
   */
  def associations: Seq[Association[R, _]] = _associations

  /**
   * Returns auxiliary objects associated with this table that will be created before all tables.
   */
  def preAuxiliaryObjects: Seq[SchemaObject] = _preAuxiliaryObjects

  /**
   * Returns auxiliary objects associated with this table that will be created after all tables.
   */
  def postAuxiliaryObjects: Seq[SchemaObject] = _postAuxiliaryObjects

  /**
   * If possible, return an association from this relation as parent to
   * specified relation as child.
   */
  def getChildAssociation[C](child: Relation[C]): Option[Association[C, R]] =
    child.getParentAssociation(this)

  /**
   * If possible, return an association from this relation as child to
   * specified relation as parent.
   */
  def getParentAssociation[P](relation: Relation[P]): Option[Association[R, P]] =
    associations.find(_.parentRelation == relation).asInstanceOf[Option[Association[R, P]]]

  /**
   * Returns column list excluding primary key column.
   */
  def nonPKColumns: Seq[Column[_, R]] =
    columns.filter(_ != primaryKey.column)

  /**
   * Returns a node that represents this relation.
   */
  def as(alias: String): RelationNode[R]

  /**
   * Adds specified associations to relation's prefetch list. These associations
   * will be prefetched every time the records are queried using <code>criteria</code>
   * method. Specified associations must relate to this relation either directly or
   * through other prefetch associations, which should appear earlier in the prefetch
   * list.
   */
  def prefetch(associations: Association[Any, Any]*): this.type = {
    _prefetchSeq ++= associations.toList
    return this
  }

  /**
   * Returns the prefetch list of this relation.
   */
  def prefetchList: Seq[Association[Any, Any]] = _prefetchSeq

  /* SIMPLE QUERIES */

  /**
   * Creates a criteria object for this relation.
   */
  def criteria: Criteria[R] =
    new Criteria(this as "root").prefetch(prefetchList: _*)

  /**
   * Queries a record by it's primary key.
   */
  def get(pk: Any): Option[R] =
    criteria.add(_.projection(primaryKey.column) eq pk).unique

  /**
   * Queries all records.
   */
  def all(): Seq[R] =
    criteria.list

  /**
   * Queries specified amount of records.
   */
  def all(limit: Int): Seq[R] =
    criteria.limit(limit).list

  /**
   * Queries specified amount of records, starting from specified offset.
   */
  def all(limit: Int, offset: Int): Seq[R] =
    criteria.limit(limit).offset(offset).list

  /* OBJECT DEFINITIONS */

  /**
   * Creates primary key constraint based on specified column.
   */
  protected[orm] def primaryKey[T](column: Column[T, R]) = {
    val constrName = relationName + "_" + column.columnName + "_pkey";
    this._primaryKey = new PhysicalPrimaryKey(this, constrName , column)
  }

  /**
   * Adds associated auxiliary object.
   */
  protected[orm] def addAuxiliaryObjects(objects: SchemaObject*) =
    addPostAuxiliaryObjects(objects.toList: _*)

  /**
   * Adds associated auxiliary object.
   */
  protected[orm] def addPreAuxiliaryObjects(objects: SchemaObject*) = {
    _preAuxiliaryObjects ++= objects.toList
  }

  /**
   * Adds associated auxiliary object.
   */
  protected[orm] def addPostAuxiliaryObjects(objects: SchemaObject*) = {
    _postAuxiliaryObjects ++= objects.toList
  }

  /**
   * Adds a unique constraint.
   */
  protected[orm] def unique(columns: Column[_, R]*): UniqueKey[R] = {
    val constrName = relationName + "_" +
    columns.map(_.columnName).mkString("_") + "_key"
    val constr = new UniqueKey(this, constrName, columns.toList)
    _constraints += constr

    constr
  }

  /**
   * Adds an associative foreign key constraint.
   */
  protected[orm] def foreignKey[T, P](parentRelation: Relation[P],
                                      column: Column[T, R]
  ): AssociativeForeignKey[T, R, P] = {
    val constrName = relationName + "_" + column.columnName + "_fkey"
    val fk = new AssociativeForeignKey(this, parentRelation, constrName, column)
    _constraints += fk
    _associations += fk

    fk
  }

  /**
   * Adds a foreign key constraint.
   */
  protected[orm] def foreignKey[T, P](
    parentRelation: Relation[P],
    localColumns: Seq[Column[_, R]],
    referenceColumns: Seq[Column[_, P]]
  ): ForeignKey[R, P] = {
    val constrName = relationName + "_" +
    localColumns.map(_.columnName).mkString("_") + "_fkey"
    val fk = new MultiForeignKey(this, parentRelation, constrName,
                                 localColumns, referenceColumns)
    _constraints += fk

    fk
  }

  /**
   * Adds a foreign key constraint.
   */
  protected[orm] def foreignKey[T, P](
    parentRelation: Relation[P],
    colPairs: Pair[Column[_, R], Column[_, P]]*
  ): ForeignKey[R, P] = {
    val localColumns = colPairs.toList.map(_._1)
    val referenceColumns = colPairs.toList.map(_._2)

    foreignKey(parentRelation, localColumns, referenceColumns)
  }

  /**
   * Adds a check constraint with specified name.
   */
  protected[orm] def check(constraintName: String, expression: String): CheckConstraint[R] = {
    val chk = new CheckConstraint(this, constraintName, expression)
    _constraints += chk

    chk
  }

  /**
   * Adds a check constraint with default name.
   */
  protected[orm] def check(expression: String): CheckConstraint[R] =
    check(relationName + "_check" + uniqueSuffix, expression)

  /**
   * Adds an index with specified name.
   */
  protected[orm] def index(indexName: String): Index[R] = {
    val idx = new Index(this, indexName)
    _postAuxiliaryObjects += idx

    idx
  }

  /**
   * Adds an index with default name.
   */
  protected[orm] def index(): Index[R] =
    index(relationName + "_idx" + uniqueSuffix)

  /**
   * Adds one or more columns to relation's definition.
   */
  protected[orm] def addColumns(cols: Column[_, R]*): this.type = {
    _columns ++= cols.toList

    this
  }

  /**
   * Adds an arbitrary column.
   */
  protected[orm] def column[T](name: String, sqlType: String): Column[T, R] = {
    val col = new Column[T, R](this, name, sqlType)
    addColumns(col)

    col
  }

  /**
   * Adds an integer column.
   */
  protected[orm] def intColumn(name: String): IntegerColumn[R] = {
    val col = new IntegerColumn(this, name)
    addColumns(col)

    col
  }

  /**
   * Adds a bigint column.
   */
  protected[orm] def longColumn(name: String): LongColumn[R] = {
    val col = new LongColumn(this, name)
    addColumns(col)

    col
  }

  /**
   * Adds a numeric column.
   */
  protected[orm] def numericColumn(name: String, precision: Int, scale: Int): NumericColumn[R] = {
    val col = new NumericColumn(this, name, precision, scale)
    addColumns(col)

    col
  }

  /**
   * Adds a string column.
   */
  protected[orm] def stringColumn(name: String): StringColumn[R] = {
    val col = new StringColumn(this, name)
    addColumns(col)

    col
  }

  /**
   * Adds a varchar column with limited maximum capacity.
   */
  protected[orm] def stringColumn(name: String, size: Int): StringColumn[R] = {
    val col = new StringColumn(this, name, size)
    addColumns(col)

    col
  }

  /**
   * Adds a varchar column with specified SQL type.
   */
  protected[orm] def stringColumn(name: String, sqlType: String): StringColumn[R] = {
    val col = new StringColumn(this, name, sqlType)
    addColumns(col)

    col
  }

  /**
   * Adds a varbinary column with limited maximum capacity.
   */
  protected[orm] def binaryColumn(name: String, size: Int): BinaryColumn[R] = {
    val col = new BinaryColumn(this, name, size)
    addColumns(col)

    col
  }

  /**
   * Adds a boolean column.
   */
  protected[orm] def booleanColumn(name: String): BooleanColumn[R] = {
    val col = new BooleanColumn(this, name)
    addColumns(col)

    col
  }

  /**
   * Adds a timestamp column.
   */
  protected[orm] def timestampColumn(name: String): TimestampColumn[R] = {
    val col = new TimestampColumn(this, name)
    addColumns(col)

    col
  }

  /**
   * Adds a date column.
   */
  protected[orm] def dateColumn(name: String): DateColumn[R] = {
    val col = new DateColumn(this, name)
    addColumns(col)

    col
  }

  /**
   * Adds a time column.
   */
  protected[orm] def timeColumn(name: String): TimeColumn[R] = {
    val col = new TimeColumn(this, name)
    addColumns(col)

    col
  }

  /* VALIDATION */

  /**
   * Returns None if record has passed validation. Otherwise returns
   * a <code>ValidationError</code> sequence.
   */
  def validate(record: Record[R]): Option[Seq[ValidationError]] = {
    val errors = validators.flatMap(_.apply(record))
    if (errors.size == 0) None else Some(errors)
  }

  /**
   * Throws <code>ValidationException</code> if record has failed validation.
   */
  def validate_!(record: Record[R]) = validate(record) match {
    case Some(errors) => throw new ValidationException(errors: _*)
    case _ =>
  }

  /**
   * Adds a field validator.
   */
  protected[orm] def addFieldValidator(col: Column[_, R], validator: Validator): RecordValidator[R] = {
    val v = new RecordFieldValidator(col, validator)
    _validators += v

    v
  }

  /* PERSISTENCE STUFF */

  private def setParams(record: Record[R], st: PreparedStatement, cols: Seq[Column[_, R]]) =
    (0 until cols.size).foreach(ix => {
        val col = cols(ix)
        val value = record.getField(col) match {
          case Some(v) => v
          case _ => null
        }
        typeConverter.write(st, value, ix + 1)
      })

  def insert(record: Record[R]): Int = {
    validate_!(record)
    insert_!(record)
  }

  def insert_!(record: Record[R]): Int = {
    if (readOnly)
      throw new ORMException("The relation " + qualifiedName + " is read-only.")
    
    transactionManager.dml(conn => {
        val sql = dialect.insertRecord(record)
        sqlLog.debug(sql)
        auto(conn.prepareStatement(sql))(st => {
            setParams(record, st, columns.filter(c => c.default == None))
            st.executeUpdate
          })
      })
  }

  def batchInsert(records: Seq[Record[R]]): Int = {
    records foreach validate_!
    batchInsert_!(records)
  }

  def batchInsert_!(records: Seq[Record[R]]): Int = {
    if (readOnly)
      throw new ORMException("The relation " + qualifiedName + " is read-only.")

    transactionManager.dml(conn => {
        var st: PreparedStatement = null
        for (record <- records) {
          val sql = dialect.insertRecord(record)
          if (st == null) {
            sqlLog.debug(sql)
            st = conn.prepareStatement(sql)
          }
          setParams(record, st, columns.filter(c => c.default == None))
          st.addBatch
        }
        if (st != null) {st.executeBatch; 1} else 0
      })
  }

  def refetchLast(record: Record[R]): Unit = {
    val expr = new SimpleExpression("root." + primaryKey.column.columnName +
                                    " = " + dialect.lastIdExpression(this), Nil)
    as("root").criteria.add(expr).unique match {
      case Some(r: Record[R]) =>
        r.fieldsMap.foreach(t => record.fieldsMap += t)
      case _ => throw new ORMException("Could not locate the last inserted row.")
    }
  }

  def update(record: Record[R]): Int = {
    validate_!(record)
    update_!(record)
  }

  def update_!(record: Record[R]): Int = {
    if (readOnly)
      throw new ORMException("The relation " + qualifiedName + " is read-only.")
    
    transactionManager.dml(conn => {
        val sql = dialect.updateRecord(record)
        sqlLog.debug(sql)
        auto(conn.prepareStatement(sql))(st => {
            setParams(record, st, nonPKColumns)
            typeConverter.write(
              st,
              record.primaryKey.get,
              nonPKColumns.size + 1)
            return st.executeUpdate
          })
      })
  }

  def save(record: Record[R]): Int = {
    validate_!(record)
    save_!(record)
  }

  def save_!(record: Record[R]): Int =
    if (record.identified_?) {
      update_!(record)
    } else {
      val rows = insert_!(record)
      refetchLast(record)
      return rows
    }

  def delete(record: Record[R]): Int = {
    if (readOnly)
      throw new ORMException("The relation " + qualifiedName + " is read-only.")
    
    transactionManager.dml(conn => {
        val sql = dialect.deleteRecord(record)
        sqlLog.debug(sql)
        auto(conn.prepareStatement(sql))(st => {
            typeConverter.write(st, record.primaryKey.get, 1)
            return st.executeUpdate
          })
      })
  }

  /* EQUALITY AND OTHER STUFF */

  override def toString = qualifiedName

  override def equals(obj: Any) = obj match {
    case rel: Relation[R] => rel.qualifiedName.equalsIgnoreCase(this.qualifiedName)
    case _ => false
  }

  override def hashCode = this.qualifiedName.toLowerCase.hashCode
}