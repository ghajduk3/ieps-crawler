package com.ieps.crawler.utils

import com.ieps.crawler.db.DBService
import com.ieps.crawler.db.Tables.PageRow
import com.typesafe.scalalogging.StrictLogging
import slick.jdbc.PostgresProfile.api.Database

class DuplicateLinks(db: Database) extends StrictLogging {
  private val dbService = new DBService(db)

  def isDuplicatePage(pageRow: PageRow): Boolean = {
    val isUrlUnique = dbService.pageExists(pageRow)
    // TODO: add hashing and LSH
    isUrlUnique
  }

  def deduplicatePages(pageRows: List[PageRow]): List[PageRow] = {
    val uniqueUrls = pageRows.zip(dbService.pageExists(pageRows)).filter(!_._2).map(_._1)
    // TODO: add hashing and LSH
    uniqueUrls
  }
}
