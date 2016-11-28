# Graph Vertex Coloring in Spark GraphX / GraphFrames
In graph theory, graph coloring or vertex/node coloring or k-coloring is an assignment of colors to graph vertices/nodes such that no two vertices which are connected should share the same color.

One can define graph coloring as

 Given an undirected graph G = (V, E), a k-color coloring assigns a color
   - color(v) ∈ {1, 2, ..., k} to each node v ∈ V
   - such that for ∀(u, v) ∈ E,  color(u) != color(v)

Bounds on chromatic number  χ(G) = minG(k)

  - 1-coloring is possible if and only if G is edgeless
  - 2-coloring is possible if and only if G is a bipartite graph.
 
 In general, a proven greedy coloring shows that every graph can be colored with one more color than the maximum vertex degree ∆(G)

    χ(G) <= maximum vertex degree + 1
    χ(G) <=  ∆(G) + 1

Graph Vertex Coloring is also popularly known as K-MinColoring where K represents the minimum number of colors possible to color the graph such that no two neighbors has the same color.

In this algorithm, we try to solve graph coloring by considering
    
        k =  ∆(G) + 1

   ∆(G) is represented as ∆ for simplicity.
   
# Native Implementation
 *******************************************
 A simple naive algorithm  for ColorReduction
 *******************************************

    function ColorReduction(G = (V, E), ∆)
        n = |V |
        for v = Range(n) do
            color(v) = v
        end for
        
        for v = ∆ + 2, n do
            color(v) = min({1, ..., ∆ + 1} \ {cu|(u, v) ∈ E})
        end for
     end function


    /*    
        color(v) = min({1, ..., ∆+1 } \ {cu|(u, v) ∈ E}) 
        indicates that a node should choose a min value from  
        (1,...,∆ + 1) which is not taken by its neighbors.
        
        For example, if my neighbors are colored (1,2,4,5) , 
        then the node will choose its color as 3.
    */
    

# Fast Implementation for Connected Graphs
The time complexity of the naive process is not efficient. An efficient version is highly fast compared to naive but works only on a connected graph. If the graph is disconnected , it should be made sure to initialize atleast one value in each connected component to  be initialized with  a value in range of (1, k+1)

  * This works on the principle of the principle of breadth first expansion
  ********************************************
    A Fast algorithm  for ColorReduction
  *******************************************
    function ColorReductionFast(G = (V, E), ∆, maxIter)
        
        n = |V |
        for v = Range(n) do
            color(v) = v
        end for

        COLORED = {node | color(node) <= ( ∆ +1 ) } 
        NOT_COLORED = {node | color(node) > ( ∆ +1 ) } 
        
        for e ∈ (u,v) | u ∈ COLORED && v ∈ NOT_COLORED {
            {  c(v) = min({1, ..., ∆ + 1} \ {cu|(u, v) ∈ E}) }

                        (or )

        for iter = 1 , maxIter do
            // updates colors of the neighbors of nodes whose colors are are already labelled.
            color(v) = min({1, ..., ∆ + 1} \ {cu|(u, v) ∈ E}) such that color(u) < ∆ +1  && color (v) > (∆ + 1)
       end for
    end function
    
More info @  https://en.wikipedia.org/wiki/Graph_coloring


More info @  https://en.wikipedia.org/wiki/Graph_coloring

