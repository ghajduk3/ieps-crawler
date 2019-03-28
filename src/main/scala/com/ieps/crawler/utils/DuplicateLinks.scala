package com.ieps.crawler.utils

import com.ieps.crawler.db.DBService
import com.ieps.crawler.db.Tables.PageRow
import com.typesafe.scalalogging.StrictLogging
import slick.jdbc.PostgresProfile.api.Database

class DuplicateLinks(db: Database) extends StrictLogging {
  private val dbService = new DBService(db)

  def isDuplicatePage(pageRow: PageRow): Boolean = {
    val isUrlUnique = dbService.pageExistsByUrl(pageRow)
    isUrlUnique
  }

  def deduplicatePages(pageRows: List[PageRow]): List[PageRow] = {
    val duplicateUrls = dbService.pageExistsByUrl(pageRows).toList.map(_.url)
    pageRows.filter(page => !duplicateUrls.contains(page.url))
  }
}
