package visualizer.models

import scalaswingcontrib.tree.Tree.Path
import scalaswingcontrib.tree._
import visualizer.TreadleController

import scala.annotation.tailrec
import scala.swing.Publisher
import scala.util.matching.Regex

/** This manages the signal available for selection.
  * The list may be filtered.
  *
  */
class SelectionModel extends Publisher {

  ///////////////////////////////////////////////////////////////////////////
  // Directory Tree Model and Pure Signals
  ///////////////////////////////////////////////////////////////////////////
  var directoryTreeModel: InternalTreeModel[GenericTreeNode] = InternalTreeModel.empty[GenericTreeNode]
  val RootPath: Tree.Path[GenericTreeNode] = Tree.Path.empty[GenericTreeNode]

  var dataModelFilter: SelectionModelFilter = SelectionModelFilter()

  def insertUnderSorted(parentPath: Path[GenericTreeNode], newValue: GenericTreeNode): Boolean = {
    val children = directoryTreeModel.getChildrenOf(parentPath)

    @tailrec def search(low: Int = 0, high: Int = children.length - 1): Int = {
      if (high <= low) {
        if (DirectoryNodeOrdering.compare(newValue, children(low)) > 0) low + 1 else low
      } else {
        val mid = (low + high) / 2
        DirectoryNodeOrdering.compare(newValue, children(mid)) match {
          case i if i > 0 => search(mid + 1, high)
          case i if i < 0 => search(low, mid - 1)
          case _ =>
            throw new Exception("Duplicate node cannot be added to the directory tree model")
        }
      }
    }

    val index = if (children.isEmpty) 0 else search()
    directoryTreeModel.insertUnder(parentPath, newValue, index)
  }

  def addSignalToSelectionList(fullName: String, signal: Signal[_ <: Any]): Unit = {
    // the full name of the signal (from treadle) uses periods to separate modules
    val fullPath = fullName.split("\\.")
    val signalName = fullPath.last
    val modules = fullPath.init

    if (dataModelFilter.patternRegex.findFirstIn(fullName).isDefined) {
      if (!(signalName.endsWith("_T") || signalName.contains("_T_")) || dataModelFilter.showTempVariables) {
        if (!(signalName.endsWith("_GEN") || signalName.contains("_GEN_")) || dataModelFilter.showGenVariables) {
          val parentPath = modules.foldLeft(RootPath) { (parentPath, module) =>
            val node = WaveFormNode(module, signal)
            val children = directoryTreeModel.getChildrenOf(parentPath)
            if (!children.contains(node)) {
              insertUnderSorted(parentPath, node)
            }
            parentPath :+ node
          }
          val node = WaveFormNode(signalName, signal)
          insertUnderSorted(parentPath, node)
        }
      }
    }
  }

  def updateTreeModel(): Unit = {
    directoryTreeModel = InternalTreeModel.empty[GenericTreeNode]

    TreadleController.dataModel.nameToSignal.foreach {
      case (name, signal) =>
        addSignalToSelectionList(name, signal)
    }
  }
}

object DirectoryNodeOrdering extends Ordering[GenericTreeNode] {
  // Sort order: Submodules, Pure signals that are registers, Mixed Signals, Other pure signals
  def compare(x: GenericTreeNode, y: GenericTreeNode): Int = {
    //TODO: re-institutes coherent sort
    //    (x, y) match {
    //      case (a:    DirectoryNode, b: DirectoryNode) => a.name.toLowerCase.compareTo(b.name.toLowerCase)
    //      case (_, _: DirectoryNode) => 1
    //      case (_: DirectoryNode, b) => -1
    //      case (a: SignalTreeNode, b: SignalTreeNode) =>
    //        (a.signal, b.signal) match {
    //          case (i:    PureSignal, j: PureSignal) => i.sortGroup - j.sortGroup
    //          case (_:    PureSignal, __) => 1
    //          case (_, _: PureSignal) => -1
    //          case (_, _) => a.name.toLowerCase.compareTo(b.name.toLowerCase)
    //        }
    //      case _ => x.name.toLowerCase.compareTo(y.name.toLowerCase)
    //    }
    x.name.toLowerCase.compareTo(y.name.toLowerCase)
  }
}

/** Used to control what shows up in the signal selection
  *
  * @param showTempVariables show _T temp wires
  * @param showGenVariables  show _GEN generated wires
  * @param showOnlyRegisters only show registers
  * @param pattern           add a search pattern
  */
case class SelectionModelFilter(
                                 showTempVariables: Boolean = false,
                                 showGenVariables: Boolean = false,
                                 showOnlyRegisters: Boolean = false,
                                 pattern: String = ".*"
                               ) {
  val patternRegex: Regex = pattern.r
}
