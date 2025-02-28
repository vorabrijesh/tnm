package visualisation.graph

import util.HeapNStorage
import visualisation.entity.EdgeInfo
import visualisation.entity.EdgeThreeJS
import visualisation.entity.GraphDataThreeJS
import visualisation.entity.NodeThreeJS

class FilesOwnershipGraph(
    val data: Map<Int, Map<Int, Float>>,
    val idToUser: Map<Int, String> = HashMap(),
    val idToFile: Map<Int, String> = HashMap()
) : GraphThreeJS("graph.js") {

    override fun generateData(size: Int, descending: Boolean): GraphDataThreeJS {
        val comparator = if (descending) compareByDescending<EdgeInfo> { it.weight } else compareBy { it.weight }
        val edgeStorage = HeapNStorage(size, comparator)
        for (entry in data) {
            val user = idToUser[entry.key] ?: "user: ${entry.key}"
            for (entry2 in entry.value) {
                val file = idToFile[entry2.key] ?: "file: ${entry2.key}"
                val weight = entry2.value

                edgeStorage.add(
                    EdgeInfo(
                        user,
                        file,
                        weight
                    )
                )
            }
        }

        val nodes = mutableSetOf<NodeThreeJS>()
        for (edge in edgeStorage) {
            nodes.add(NodeThreeJS(edge.source))
            nodes.add(NodeThreeJS(edge.target, shape = 1))
        }

        return GraphDataThreeJS(
            nodes.toList(),
            edgeStorage.map {
                val value =
                    normalizeMinMax(it.weight, edgeStorage.low!!.weight, edgeStorage.high!!.weight)
                EdgeThreeJS(
                    it.source,
                    it.target,
                    it.weight,
                    value = value,
                    edgeColor(value)
                )
            })
    }
}
