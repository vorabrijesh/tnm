package miners.gitMiners

import TestConfig.branch
import TestConfig.gitDir
import dataProcessor.FilesOwnershipDataProcessor
import org.junit.Test
import util.ProjectConfig
import kotlin.test.assertTrue

internal class FilesOwnershipMinerTests : GitMinerTest {

    @Test
    fun `test one thread and multithreading`() {
        val mapOneThread = runMiner(1)
        val mapMultithreading = runMiner()
        compareMapsOfMapsDouble(mapOneThread, mapMultithreading)
    }

    private fun runMiner(
        numThreads: Int = ProjectConfig.DEFAULT_NUM_THREADS
    ): Map<String, Map<String, Float>> {
        val dataProcessor = FilesOwnershipDataProcessor()
        val miner = FilesOwnershipMiner(gitDir, numThreads = numThreads, neededBranch = branch)
        miner.run(dataProcessor)

        assertTrue(dataProcessor.developerKnowledge.isNotEmpty())

        return changeIdsToValuesInMapOfMaps(
            dataProcessor.developerKnowledge,
            dataProcessor.idToUser,
            dataProcessor.idToFile
        )
    }
}
