package cjp.payments.util

import play.api.mvc.{Session, Request}


class SessionParameter(val name: String) {

  def find()(implicit request: Request[_]): Option[String] = {
    request.session.get(name)
  }

  def find[B >: String](default : => B)(implicit request: Request[_]): B = {
    request.session.get(name).getOrElse(default)
  }

  def get(implicit request: Request[_]): String = {
    find(default = unknown)
  }

  def unknown = throw new MissingSessionParameter(name)


  def remove(implicit request: Request[_]): Session = {
    request.session - name
  }

  def add(value: String)(implicit request: Request[_]): Session = {
    request.session + (name -> value)
  }

  def apply(value: String): (String, String) = name -> value

}
