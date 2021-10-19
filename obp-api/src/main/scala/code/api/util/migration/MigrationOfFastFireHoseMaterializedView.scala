package code.api.util.migration

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.productfee.ProductFee
import net.liftweb.mapper.{DB, Schemifier}
import net.liftweb.util.DefaultConnectionIdentifier

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

object MigrationOfFastFireHoseMaterializedView {

  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")

  def addFastFireHoseMaterializedView(name: String): Boolean = {
    DbFunction.tableExists(ProductFee, (DB.use(DefaultConnectionIdentifier){ conn => conn})) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        val executedSql =
          DbFunction.maybeWrite(true, Schemifier.infoF _, DB.use(DefaultConnectionIdentifier){ conn => conn}) {
            () =>
              """
                |CREATE MATERIALIZED VIEW mv_fast_firehose_accounts AS select
                |    mappedbankaccount.theaccountid as account_id,
                |    mappedbankaccount.bank as bank_id,
                |    mappedbankaccount.accountlabel as account_label,
                |    mappedbankaccount.accountnumber as account_number,
                |    (select
                |        string_agg(
                |            'user_id:'
                |            || resourceuser.userid_
                |            ||',provider:'
                |            ||resourceuser.provider_
                |            ||',user_name:'
                |            ||resourceuser.name_,
                |         ',') as owners
                |     from resourceuser
                |     where
                |        resourceuser.id = mapperaccountholders.user_c
                |    ),
                |    mappedbankaccount.kind as kind,
                |    mappedbankaccount.accountcurrency as account_currency ,
                |    mappedbankaccount.accountbalance as account_balance,
                |    (select 
                |        string_agg(
                |            'bank_id:'
                |            ||bankaccountrouting.bankid 
                |            ||',account_id:' 
                |            ||bankaccountrouting.accountid,
                |            ','
                |            ) as account_routings
                |        from bankaccountrouting
                |        where 
                |              bankaccountrouting.accountid = mappedbankaccount.theaccountid
                |     ),                                                          
                |    (select 
                |        string_agg(
                |                'type:'
                |                || mappedaccountattribute.mtype
                |                ||',code:'
                |                ||mappedaccountattribute.mcode
                |                ||',value:'
                |                ||mappedaccountattribute.mvalue,
                |            ',') as account_attributes
                |    from mappedaccountattribute
                |    where
                |         mappedaccountattribute.maccountid = mappedbankaccount.theaccountid
                |     )
                |from mappedbankaccount
                |         LEFT JOIN mapperaccountholders
                |                   ON (mappedbankaccount.bank = mapperaccountholders.accountbankpermalink and mappedbankaccount.theaccountid = mapperaccountholders.accountpermalink);
                |CREATE INDEX account_id ON mv_fast_firehose_accounts(account_id);
                |CREATE INDEX bank_id ON mv_fast_firehose_accounts(bank_id);
                |""".stripMargin
          }

        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""Executed SQL: 
             |$executedSql
             |""".stripMargin
        isSuccessful = true
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful

      case false =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        val isSuccessful = false
        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""${ProductFee._dbTableNameLC} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }

}