package AbstractInterpreter

import Alias.Trace
import cats._
import cats.data._
import cats.implicits._

  object Trace {

    def pure[S,W: Monoid,A](a: A): Trace[S, W, A] = StateT.pure[WriterT[List, W, ?], S, A](a)

    def get[S,W: Monoid]: Trace[S, W, S] = StateT.get[WriterT[List, W, ?], S]

    def set[S,W: Monoid](s: S): Trace[S, W, Unit] = StateT.set[WriterT[List, W, ?], S](s)

    def modify[S,W: Monoid](f: S => S): Trace[S, W, Unit] = StateT.modify[WriterT[List, W, ?], S](f)

    def tell[S,W: Monoid](w: W): Trace[S, W, Unit] = StateT.liftF[WriterT[List, W, ?], S, Unit](WriterT.tell(w))

    def fromList[S,W: Monoid,A](xs: List[A]): Trace[S, W, A] = StateT.liftF[WriterT[List,W,?],S,A](WriterT.liftF(xs))

  }
