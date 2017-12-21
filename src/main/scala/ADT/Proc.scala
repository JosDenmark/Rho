package ADT

import cats.{
  Applicative,
  Bifoldable,
  Bifunctor,
  Bitraverse,
  Eval,
  Foldable,
  Functor,
  Monad,
  Traverse
}

// X := X[P[X]]
case class Rho(proc: Proc[Rho])

// P[X]
sealed trait Proc[Chan]
  // 0
  case class Zero[Chan]() extends Proc[Chan]
  // X!P[X]
  case class Output[Chan](x: Chan, p: Proc[Chan]) extends Proc[Chan]
  // for ( Z <- X )P[Z]
  case class Input[Chan](x: Chan, p: Scope[Proc[?],Unit,Chan]) extends Proc[Chan]
  // P[X] | P[X]
  case class Par[Chan](left: Proc[Chan], right: Proc[Chan]) extends Proc[Chan]
  // *X
  case class Drop[Chan](x: Chan) extends Proc[Chan]

/*
 * The uninhabited type.
 */
case class Void(z: Void)

object Void {

  /**
   * Logical reasoning of type 'ex contradictione sequitur quodlibet'
   */

  def absurd[A](z: Void): A = absurd(z)

  def vacuous[F[_], A](fa: F[Void], z: Void)(implicit F: Functor[F]): F[A] = F.map(fa)(absurd(z))

  // implicit def voidSemiGroup: Semigroup[Void] = new Semigroup[Void] {
  //   def append(f1: Void, f2: => Void) = f2 //right biased
  // }
}

trait Chan[A, B]
  case class Var[A,B](chan:A) extends Chan[A,B]
  case class Quote[A,B](proc:B) extends Chan[A,B]

object Chan {
  implicit def functorChan[A]: Functor[Chan[A,?]] = new Functor[Chan[A,?]]{
    def map[B,D](chan: Chan[A,B])(f:B => D): Chan[A,D]
      = chan match {
      case Var(ch) => Var(ch)
      case Quote(proc) => Quote(f(proc))
    }
  }

  implicit def bifunctorChan: Bifunctor[Chan] = new Bifunctor[Chan] {
    def bimap[A, B, C, D](chan: Chan[A,B])
                         (f: A => C, g: B => D): Chan[C, D]
    = chan match {
      case Var(ch) => Var(f(ch))
      case Quote(proc) => Quote(g(proc))
    }
  }

  implicit def bifoldableChan: Bifoldable[Chan] = new Bifoldable[Chan] {
    def bifoldLeft[A, B, C]
      (fab: Chan[A, B], c: C)
      (f: (C, A) => C, g: (C, B) => C): C
      = fab match {
        case Var(ch) => f(c,ch)
        case Quote(proc) => g(c,proc)
      }
    def bifoldRight[A, B, C]
      (fab: Chan[A, B], c: Eval[C])
      (f: (A, Eval[C]) => Eval[C], g: (B, Eval[C]) => Eval[C]): Eval[C]
      = fab match {
        case Var(ch) => f(ch,c)
        case Quote(proc) => g(proc,c)
      }
  }

  implicit def foldableChan[Ch]: Foldable[Chan[Ch,?]] = new Foldable[Chan[Ch,?]] {
    def foldLeft[A, B](fa: Chan[Ch,A], b: B)(f: (B, A) => B): B
    = fa match {
      case Var(ch) => b
      case Quote(proc) => f(b,proc)
    }
    def foldRight[A, B](fa: Chan[Ch,A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B]
    = fa match {
      case Var(ch) => lb
      case Quote(proc) => f(proc,lb)
    }
  }

  implicit def bitraverseChan: Bitraverse[Chan] = new Bitraverse[Chan]{
    def bifoldLeft[A, B, C]
      (fab: Chan[A, B], c: C)
      (f: (C, A) => C, g: (C, B) => C): C
      = Chan.bifoldableChan.bifoldLeft(fab,c)(f,g)
    def bifoldRight[A, B, C]
      (fab: Chan[A, B], c: Eval[C])
      (f: (A, Eval[C]) => Eval[C], g: (B, Eval[C]) => Eval[C]): Eval[C]
      = Chan.bifoldableChan.bifoldRight(fab,c)(f,g)
    def bitraverse[G[_], A, B, C, D]
      (fab: Chan[A, B])
      (f: (A) => G[C], g: (B) => G[D])
      (implicit arg0: Applicative[G]): G[Chan[C, D]]
      = fab match {
        case Var(ch) => arg0.map (f(ch)) (Var(_))
        case Quote(proc) => arg0.map (g(proc)) (Quote(_))
      }
  }

  implicit def traverseChan[Var]: Traverse[Chan[Var,?]] = new Traverse[Chan[Var,?]]{
    def foldLeft[A, B](fa: Chan[Var,A], b: B)(f: (B, A) => B): B
    = Chan.foldableChan.foldLeft(fa,b)(f)
    def foldRight[A, B](fa: Chan[Var,A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B]
    = Chan.foldableChan.foldRight(fa,lb)(f)
    def traverse[G[_], A, B](fa: Chan[Var,A])(f: (A) => G[B])(implicit arg0: Applicative[G]): G[Chan[Var,B]]
    = fa match {
      case Var(x) => arg0.pure(Var(x))
      case Quote(proc) => arg0.map (f(proc)) (Quote(_))
    }
  }

}

case class Scope[F[_],A,B](unscope: F[Chan[A,F[B]]])

object Scope {

  implicit def bifunctorScope[F[_]](implicit F: Functor[F]): Bifunctor[Scope[F, ?, ?]] = new Bifunctor[Scope[F, ?, ?]] {
    def bimap[A, B, C, D](scope: Scope[F, A, B])(fa: A => C, fb: B => D): Scope[F, C, D] = {
      Scope[F, C, D](F.map(scope.unscope)(Chan.bifunctorChan.bimap(_)(fa, F.map(_)(fb))))
    }
  }

  implicit def functorScope[F[_],A](implicit F: Functor[F]): Functor[Scope[F, A, ?]] = new Functor[Scope[F, A, ?]] {
    def map[B, D](scope: Scope[F, A, B])(f: B => D): Scope[F, A, D] = bifunctorScope.bimap[A, B, A, D](scope)(identity, f)
  }

  implicit def bifoldableScope[F[_]](implicit F: Foldable[F]): Bifoldable[Scope[F, ?, ?]] = new Bifoldable[Scope[F,?,?]] {
    def bifoldLeft[A, B, C]
      (fab: Scope[F, A, B], c: C)
      (f: (C, A) => C, g: (C, B) => C): C
      = F.foldLeft (fab.unscope,c) ((acc,ch) => Chan.bifoldableChan.bifoldLeft(ch,acc)(f,(cc,fb) => F.foldLeft (fb,cc) (g)))
    def bifoldRight[A, B, C]
      (fab: Scope[F, A, B], c: Eval[C])
      (f: (A, Eval[C]) => Eval[C], g: (B, Eval[C]) => Eval[C]): Eval[C]
      = F.foldRight (fab.unscope,c) ((ch,acc) => Chan.bifoldableChan.bifoldRight(ch,acc)(f,(cc,fb) => F.foldRight (cc,fb) (g)))
  }

  implicit def bitraverseScope[F[_]](implicit F: Traverse[F]): Bitraverse[Scope[F,?,?]] = new Bitraverse[Scope[F,?,?]]{
    def bifoldLeft[A, B, C]
      (fab: Scope[F, A, B], c: C)
      (f: (C, A) => C, g: (C, B) => C): C
      = Scope.bifoldableScope.bifoldLeft(fab,c)(f,g)
    def bifoldRight[A, B, C]
      (fab: Scope[F, A, B], c: Eval[C])
      (f: (A, Eval[C]) => Eval[C], g: (B, Eval[C]) => Eval[C]): Eval[C]
      = Scope.bifoldableScope.bifoldRight(fab,c)(f,g)
    def bitraverse[G[_], A, B, C, D]
      (fab: Scope[F,A, B])
      (f: (A) => G[C], g: (B) => G[D])
      (implicit arg0: Applicative[G]): G[Scope[F, C, D]]
      = arg0.map (F.traverse (fab.unscope) (chan => Chan.bitraverseChan.bitraverse (chan) (f,F.traverse (_) (g)))) (Scope(_))
  }

  implicit def scopeFlatten[F[_],A, B, C](scope: Scope[F, A, B])(f: B => F[C])(implicit F: Monad[F]): Scope[F, A, C] = {
    val m = scope.unscope
    Scope[F, A, C](F.flatMap(m)({
      case Var(ch) => F.pure(Var[A, F[C]](ch))
      case Quote(proc) => F.map(F.map(proc)(f))(Quote(_))
    }))
  }

  implicit def abstracT0[F[_],A,B](p: F[A])(f:A => Option[B])(implicit F: Monad[F]): Scope[F,B,A] = {
    val k: A => Chan[B,F[A]] = y => {
      f(y) match {
        case Some(z) => Var(z)
        case None => Quote(F.pure(y))
      }
    }
    Scope[F,B,A](F.map(p)(k))
  }

  implicit def abstracT1[F[_],A](a: A)(p: F[A])(implicit F: Monad[F]): Scope[F,Unit,A] = {
    Scope.abstracT0[F,A,Unit](p)(b => if(a == b) Some(Unit) else None)
  }

}

object Proc {

  implicit val functorProc: Functor[Proc] = new Functor[Proc]{
    def map[A, B](proc: Proc[A])(func: A => B): Proc[B] =
      proc match {
        case Zero() => Zero()
        case Drop(x) => Drop(func(x))
        case Input(x,p) => sys.error("unimplemented")
        case Output(x,p) => Output(func(x), map(p)(func))
        case Par(proc1,proc2) => Par(map(proc1)(func), map(proc2)(func))
      }
  }

  implicit val foldableProc: Foldable[Proc] = new Foldable[Proc]{
    def foldLeft[A, B](proc: Proc[A],b: B)(f: (B, A) => B): B =
      proc match {
        case Zero() => b
        case Drop(x) => f(b,x)
        case Input(x,p) => sys.error("unimplemented")
        case Output(x,p) => f(foldLeft(p,b)(f),x)
        case Par(proc1,proc2) => foldLeft(proc2,foldLeft(proc1,b)(f))(f)
      }

    def foldRight[A, B](proc: Proc[A],lb: Eval[B])(f:(A, Eval[B]) => Eval[B]): Eval[B] =
      proc match {
        case Zero() => lb
        case Drop(x) => f(x,lb)
        case Input(x,p) => sys.error("unimplemented")
        case Output(x,p) => f(x,foldRight(p,lb)(f))
        case Par(proc1,proc2) => foldRight(proc1,foldRight(proc2,lb)(f))(f)
      }
  }

  implicit val traversableProc: Traverse[Proc] = new Traverse[Proc]{

    def traverse[G[_], A, B](proc: Proc[A])(func: A => G[B])(implicit ap: Applicative[G]): G[Proc[B]] =
      proc match {
        case Zero() => ap.pure(Zero[B]())
        case Drop(x) => ap.map(func(x))(Drop[B])
        case Input(x,p) => sys.error("unimplemented")
        case Output(x,p) => ap.map2(func(x), traverse(p)(func))(Output[B])
        case Par(proc1,proc2) => ap.map2(traverse(proc1)(func), traverse(proc2)(func))(Par[B])
      }

    def foldLeft[A, B](proc: Proc[A],b: B)(f: (B, A) => B): B =
      foldableProc.foldLeft(proc,b)(f)

    def foldRight[A, B](proc: Proc[A],lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] =
      foldableProc.foldRight(proc,lb)(f)
  }

  def cost(rho: Rho): Int = {
    val unquotecost: Int = sys.error("implement unquote cost")
    val quotecost: Int = sys.error("implement quote cost")
    val bindcost: Int = sys.error("implement bind cost")
    val writecost: Int = sys.error("implement write cost")
    rho match {
      case Rho(Zero()) => 0
      case Rho(Drop(x)) => unquotecost + cost(x)
      case Rho(Input(x,proc)) => sys.error("unimplemented")
      case Rho(Output(x,proc)) => quotecost + writecost
      case Rho(Par(proc1,proc2)) => cost(Rho(proc1)) + cost(Rho(proc2))
    }
  }
}

// newtype Rho chan = Rho {unRho :: Scope chan Proc (Rho Void)}
case class Rho2[chan](proc: Scope[Proc[?],chan,Rho2[Void]])

object Rho2{
  def zero[chan]: Rho2[chan] = Rho2(Scope(Zero()))
}

/*

Abstract Interpretation:

@ : P x Env -> N
  - takes a closure and returns a reference to that closure, i.e., a name.

* : N -> P x Env
  - evaluates a reference to retrieve a closure

Env : N -> A
  - the contents of names. For free names, the contents of the channel. For bound names, the value bound to the name.

Store : A -> N

Term:
0 : 1 -> P
! : N x P -> P
for : N x P -> P
| : P x P -> P
* : N -> P
COMM : 1 -> P
@ : P -> N

*/
