package miners.gitMiners

import dataProcessor.ComplexityCodeChangesDataProcessor
import dataProcessor.ComplexityCodeChangesDataProcessor.ChangeType
import dataProcessor.ComplexityCodeChangesDataProcessor.PeriodType
import dataProcessor.inputData.FileModification
import miners.gitMiners.UtilGitMiner.isBugFixCommit
import miners.gitMiners.UtilGitMiner.isNotNeededFilePath
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.diff.RawTextComparator
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.util.io.DisabledOutputStream
import util.ProjectConfig
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap


class ComplexityCodeChangesMiner(
    repositoryFile: File,
    private val neededBranch: String,
    numThreads: Int = ProjectConfig.DEFAULT_NUM_THREADS,
    val filesToProceed: Set<String>? = null
) : GitMiner<ComplexityCodeChangesDataProcessor>(repositoryFile, setOf(neededBranch), numThreads = numThreads) {
    // Mark each commit for period
    // [commitId][periodId]
    private val markedCommits = ConcurrentHashMap<String, Int>()

    override fun process(
        dataProcessor: ComplexityCodeChangesDataProcessor,
        commit: RevCommit
    ) {
        if (!isFeatureIntroductionCommit(commit)) return

        val git = threadLocalGit.get()
        val reader = threadLocalReader.get()

        val periodId = markedCommits[commit.name]!!

        when (dataProcessor.changeType) {
            ChangeType.LINES -> {
                val diffs = reader.use { UtilGitMiner.getDiffsWithoutText(commit, it, git) }

                val diffFormatter = DiffFormatter(DisabledOutputStream.INSTANCE)
                diffFormatter.setRepository(repository)
                diffFormatter.setDiffComparator(RawTextComparator.DEFAULT)
                diffFormatter.isDetectRenames = true

                for (diff in diffs) {
                    val filePath = UtilGitMiner.getFilePath(diff)
                    if (isNotNeededFilePath(filePath, filesToProceed)) continue

                    val editList = diffFormatter.toFileHeader(diff).toEditList()
                    for (edit in editList) {
                        val numOfAddedLines = edit.endB - edit.beginB
                        val numOfDeletedLines = edit.endA - edit.beginA

                        val modifiedLines = numOfAddedLines + numOfDeletedLines

                        val data = FileModification(periodId, filePath, modifiedLines)
                        dataProcessor.processData(data)

                    }
                }
            }

            ChangeType.FILE -> {
                val changedFiles = reader.use {
                    UtilGitMiner.getChangedFiles(
                        commit, it, git
                    )
                }

                for (filePath in changedFiles) {
                    if (isNotNeededFilePath(filePath, filesToProceed)) continue

                    val data = FileModification(periodId, filePath, 1)
                    dataProcessor.processData(data)
                }
            }
        }

    }

    override fun run(dataProcessor: ComplexityCodeChangesDataProcessor) {
        UtilGitMiner.findNeededBranches(threadLocalGit.get(), neededBranches)
        markCommits(dataProcessor)

        super.run(dataProcessor)
        dataProcessor.calculate()
    }

    private fun isFeatureIntroductionCommit(commit: RevCommit): Boolean {
        return !isBugFixCommit(commit)
    }

    private fun splitInPeriods(dataProcessor: ComplexityCodeChangesDataProcessor): List<List<RevCommit>> {
        val git = threadLocalGit.get()
        val commitsInBranch = UtilGitMiner.getCommits(git, repository, neededBranch)
        if (commitsInBranch.isEmpty()) return listOf()

        return when (dataProcessor.periodType) {
            PeriodType.TIME_BASED -> {
                val firstDate = getTrimDate(commitsInBranch.first())
                val periods = mutableListOf<List<RevCommit>>()

                var upperThreshold = addMonths(firstDate, -dataProcessor.numOfMonthInPeriod)
                var period = mutableListOf<RevCommit>()
                for (commit in commitsInBranch) {
                    val commitDate = Date(commit.commitTime * 1000L)
                    if (commitDate < upperThreshold) {
                        periods.add(period)
                        period = mutableListOf()
                        upperThreshold = addMonths(upperThreshold, -dataProcessor.numOfMonthInPeriod)
                    }
                    period.add(commit)
                }

                if (period.isNotEmpty()) {
                    periods.add(period)
                }

                periods
            }
            PeriodType.MODIFICATION_LIMIT -> commitsInBranch.chunked(dataProcessor.numOfCommitsInPeriod)
        }
    }

    private fun getTrimDate(commit: RevCommit): Date {
        val date = Date(commit.commitTime * 1000L)

        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar[Calendar.MILLISECOND] = 0
        calendar[Calendar.SECOND] = 0
        calendar[Calendar.MINUTE] = 0
        calendar[Calendar.HOUR_OF_DAY] = 0
        return calendar.time
    }

    private fun addMonths(date: Date, numOfMonth: Int): Date {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.add(Calendar.MONTH, numOfMonth)
        return calendar.time
    }

    private fun markCommits(dataProcessor: ComplexityCodeChangesDataProcessor) {
        val periods = splitInPeriods(dataProcessor)
        for ((i, period) in periods.withIndex()) {
            for (commit in period) {
                markedCommits[commit.name] = i
            }
        }
    }

}
