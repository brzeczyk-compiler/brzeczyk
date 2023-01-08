" Vim syntax file
" Language: Brzeczyk
" Latest Revision: 27 December 2022

if exists("b:current_syntax")
	finish
endif

syntax case match


" Numbers
syn match nnIntegerLiteral '\<\d\+\>'

" Custom names
syn match nnCustomType '\<[A-ZĄĆĘŁŃÓŚŹŻ][A-ZĄĆĘŁŃÓŚŹŻa-ząćęłńóśźż0-9_]*\>'
syn match nnIdentifier '\<[a-ząćęłńóśźż][A-ZĄĆĘŁŃÓŚŹŻa-ząćęłńóśźż0-9_]*\>'
syn match nnForeignName '`[A-ZĄĆĘŁŃÓŚŹŻa-ząćęłńóśźż_][A-ZĄĆĘŁŃÓŚŹŻa-ząćęłńóśźż0-9_]*`'

" Keywords
syn keyword nnKeywordType Nic Czy Liczba
syn keyword nnKeywordBoolLiteral fałsz prawda
syn keyword nnKeywordOtherLiteral nic
syn keyword nnKeywordCondition jeśli wpp
syn match   nnKeywordElif '\<zaś gdy\>'
syn keyword nnKeywordLoop dopóki otrzymując od
syn keyword nnKeywordOperator nie lub oraz wtw albo
syn keyword nnKeywordOther zm wart stała przerwij pomiń zwróć zakończ czynność zewnętrzna zewnętrzny jako przekaźnik przekaż

" Ranges
syn match nnParenthesisError ')'
syn region nnParenthesisRange start='(' end=')' transparent contains=TOP,nnParenthesisError
syn match nnBracesError '}'
syn region nnBracesRange start='{' end='}' transparent fold contains=TOP,nnBracesError

" Comments
syn keyword nnSpecialCommentWord contained TODO FIXME
syn match nnComment '//.*$' contains=nnSpecialCommentWord



" Set colors
let b:current_syntax = "brzeczyk"
hi def link nnIntegerLiteral Number

hi def link nnCustomType Type
hi def link nnIdentifier Identifier
hi def link nnForeignName Identifier

hi def link nnKeywordType Type
hi def link nnKeywordBoolLiteral Boolean
hi def link nnKeywordOtherLiteral Constant
hi def link nnKeywordCondition Conditional
hi def link nnKnKeywordElif Conditional
hi def link nnKeywordLoop Repeat
hi def link nnKeywordOperator Operator
hi def link nnKeywordOther Keyword

hi def link nnParenthesisError Error
hi def link nnBracesError Error

hi def link nnSpecialCommentWord Todo
hi def link nnComment Comment
